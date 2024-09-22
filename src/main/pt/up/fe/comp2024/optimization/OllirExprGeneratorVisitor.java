package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;
import static pt.up.fe.comp2024.ast.TypeUtils.getExprType;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(IDENTIFIER, this::visitIdentifier);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOL_VALUE, this::visitBoolValue);
        addVisit(OBJECT_FUNCTION_CALL, this::visitOjectFunctionCall);
        addVisit(NEG_OPERATOR, this::visitNegOperator);
        addVisit(NEW_CLASS, this::visitNewClass);
        addVisit(NEW_ARRAY, this::visitNewArray);
        addVisit(LENGTH, this::visitLength);
        addVisit(ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(SIMPLE_ARRAY, this::visitArrayInit);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitArrayInit(JmmNode jmmNode, Void unused) {
        int array_size = jmmNode.getChildren().size();
        StringBuilder computation = new StringBuilder();
        var temp = OptUtils.getTemp();
        // TODO - Hard Coded to i32
        // Add the computation for the new array
        //tmp2.array.i32 :=.array.i32 new(array, 4.i32).array.i32;
        computation.append(temp).append(".array.i32 :=.array.i32 new(array, ").append(array_size).append(".i32).array.i32").append(END_STMT);
        String arrayTemp = array_size > 0 ? OptUtils.getTempArray() : "";

        //       __varargs_array_0.array.i32 :=.array.i32 tmp2.array.i32;
        //      __varargs_array_0.array.i32[0.i32].i32 :=.i32 1.i32;
        //      __varargs_array_0.array.i32[1.i32].i32 :=.i32 2.i32;
        //      __varargs_array_0.array.i32[2.i32].i32 :=.i32 3.i32;
        //      __varargs_array_0.array.i32[3.i32].i32 :=.i32 4.i32;
        if (array_size > 0) {
            computation.append(arrayTemp).append(".array.i32 :=.array.i32 ").append(temp).append(".array.i32").append(END_STMT);
            for (int i = 0; i < array_size; i++) {
                var childExprResult = this.visit(jmmNode.getChild(i));
                computation.append(arrayTemp).append(".array.i32[").append(i).append(".i32].i32 :=.i32 ").append(childExprResult.getCode()).append(END_STMT);
            }
        }

        String code = arrayTemp + ".array.i32";

        return new OllirExprResult(code, computation.toString());
    }

    private OllirExprResult visitArrayAccess(JmmNode jmmNode, Void unused) {
        String methodName = jmmNode.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        var array_name = jmmNode.getChild(0).get("name");
        var index = visit(jmmNode.getChild(1));
        String array_type = OptUtils.toOllirType(getExprType(jmmNode.getChild(0), table, methodName));
        // code to compute the children
        computation.append(index.getComputation());

        // code to compute self
        Type resType = getExprType(jmmNode, table, methodName);
        String resOllirType = OptUtils.toOllirType(resType);

        // t2.i32 :=.i32 a[1.i32].i32;

        // If the parent is Assign and the array access is on the right side, we don't need to add computation because it is alone on the right
        // If the parent is Assign and the array access is on the left side, we don't add any computation only the code
        if (jmmNode.getParent().isInstance(ASSIGN_STMT)) {
            code.append(array_name).append(array_type).append("[").append(index.getCode()).append("]").append(resOllirType);
        }
        // If the parent is BinaryExpr, we need to add computation
        else {
            String temp = OptUtils.getTemp();
            code.append(temp).append(resOllirType);
            computation.append(temp).append(resOllirType).append(ASSIGN).append(resOllirType).append(SPACE).append(array_name).append(array_type).append("[").append(index.getCode()).append("]").append(resOllirType).append(END_STMT);
        }

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitLength(JmmNode jmmNode, Void unused) {
        // Type of the array that the length is being computed
        String arrayType = OptUtils.toOllirType(getExprType(jmmNode.getChild(0), table, jmmNode.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow()));

        var temp = OptUtils.getTemp();
        String code = temp + ".i32";
        String computation = temp + ".i32 :=.i32 arraylength(" + jmmNode.getChild(0).get("name") + arrayType + ").i32" + END_STMT;
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNegOperator(JmmNode jmmNode, Void unused) {
        var exprResult = this.visit(jmmNode.getChild(0));
        String code = "!.bool " + exprResult.getCode();
        String computation = exprResult.getComputation();
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitBoolValue(JmmNode jmmNode, Void unused) {
        var boolType = new Type(TypeUtils.getBoolTypeName(), false);
        String ollirBoolType = OptUtils.toOllirType(boolType);
        if (jmmNode.get("name").equals("true")) {
            return new OllirExprResult("1" + ollirBoolType);
        }
        else if (jmmNode.get("name").equals("false")) {
            return new OllirExprResult("0" + ollirBoolType);
        }
        return new OllirExprResult("errorBoolValue");
    }

    private OllirExprResult visitNewArray(JmmNode jmmNode, Void unused) {
        String code = "";
        var exprResult = this.visit(jmmNode.getChild(0));
        String returnType = OptUtils.toOllirType(jmmNode);
        code += String.format("new(array, %s).array%s", exprResult.getCode(), returnType);

        return new OllirExprResult(code, exprResult.getComputation());
    }

    private OllirExprResult visitOjectFunctionCall(JmmNode node, Void unused) {
        // invokevirtual(this, "constInstr").i32
        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        JmmNode parent = node.getParent();
        StringBuilder computatation = new StringBuilder();
        String code;

        String callerObject = node.getChild(0).get("name");
        if (!node.getChild(0).getKind().equals(THIS.toString())) {
            // To know if it is a package import
            Type type = getExprType(node.getChild(0), table, methodName);
            callerObject += type.getName().equals("Imported") ? "" : OptUtils.toOllirType(type);
        }
        String returnType = OptUtils.toOllirType(getExprType(node, table, methodName));
        String functionName = node.get("name");

        StringBuilder parameters_string = new StringBuilder();

        // Parameters
        List<JmmNode> func_param = node.getDescendants(FUNC_PARAMETER);
        if (!func_param.isEmpty()) {
            List<JmmNode> parameters = func_param.get(0).getChildren();

            int expected_params_size = -1;
            List<Symbol> expected_params = new ArrayList<>();
            if (table.getMethods().contains(functionName)) {
                expected_params = table.getParameters(functionName);
                expected_params_size = expected_params.size();
            }

            for (int i = 0; i < parameters.size(); i++) {
                JmmNode parameter = parameters.get(i);
                // If it is a vararg parameter
                if (expected_params_size == 1) {
                    boolean isVarArg = TypeUtils.isVariableVarArg(expected_params.get(expected_params.size()-1).getName(), table, functionName);
                    if (isVarArg) {
                        var result = computeVarArgParameter(parameters.subList(i, parameters.size()));
                        computatation.append(result.getComputation());
                        parameters_string.append(", ");
                        parameters_string.append(result.getCode());
                        break;
                    }
                }
                expected_params_size--;
                var exprResult = this.visit(parameter);
                parameters_string.append(", ");
                parameters_string.append(exprResult.getCode());
                // Insert the computation of the parameter in case it is needed
                computatation.append(exprResult.getComputation());
            }
        }

        // If it is a separate function call
        boolean isImportedPackage = TypeUtils.isImportedPackage(node.getChild(0).get("name"), table);
        if (parent.getKind().equals(EXPR_STMT.toString())) {
            if (isImportedPackage) {
                code = "invokestatic(" + callerObject + "," + SPACE + "\"" + functionName+"\"";
                code += parameters_string.toString();
                // TODO Hard Coded to void
                code += ").V" + END_STMT;
            }
            else {
                code = String.format("invokevirtual(%s, \"%s\"%s)%s%s", callerObject, functionName, parameters_string, returnType, END_STMT);
            }
        }
        else {
            // code to compute self
            String temp = OptUtils.getTemp();
            code = temp + returnType;

            computatation.append(String.format("%s%s :=%s invokevirtual(%s, \"%s\"%s)%s%s", temp, returnType, returnType, callerObject, functionName, parameters_string, returnType, END_STMT));

        }

        return new OllirExprResult(code, computatation.toString());
    }

    private OllirExprResult computeVarArgParameter(List<JmmNode> parameters) {
        int array_size = parameters.size();
        StringBuilder computation = new StringBuilder();
        var temp = OptUtils.getTemp();
        // TODO - Hard Coded to i32
        // Add the computation for the new array
        computation.append(temp).append(".array.i32 :=.array.i32 new(array, ").append(array_size).append(".i32).array.i32").append(END_STMT);
        String arrayTemp = "";
        String code = "";
        if (array_size > 0) {
            arrayTemp = OptUtils.getTempArray();
            code = arrayTemp + ".array.i32";
        }

        if (array_size > 0) {
            computation.append(arrayTemp).append(".array.i32 :=.array.i32 ").append(temp).append(".array.i32").append(END_STMT);
            for (int i = 0; i < array_size; i++) {
                var childExprResult = this.visit(parameters.get(i));
                computation.append(arrayTemp).append(".array.i32[").append(i).append(".i32].i32 :=.i32 ").append(childExprResult.getCode()).append(END_STMT);
            }
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewClass(JmmNode node, Void unused) {
//        temp_2.Simple :=.Simple new(Simple).Simple;
//        invokespecial(temp_2.Simple,"<init>").V;
//        s.Simple :=.Simple temp_2.Simple;
        String code;
        String returnType = OptUtils.toOllirType(node);

        // code to compute self
        String temp = OptUtils.getTemp();
        code = temp + returnType;

        String computatation = String.format("%s :=%s new(%s)%s;\n" +
                "invokespecial(%s,\"<init>\").V%s", code, returnType, node.get("name"),returnType, code, END_STMT);


        return new OllirExprResult(code, computatation);
    }



        private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();

        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute self
        Type resType = getExprType(node, table, methodName);
        String resOllirType = OptUtils.toOllirType(resType);
        String code;

        // Different computation and code for "AND" operator
        if (node.get("op").equals("&&")) {
            String true_label = OptUtils.getTempTrue();
            String end_label = OptUtils.getTempEnd();
            String resultTemp = OptUtils.getTemp();
//             if (1.bool) goto true_0;
            computation.append(lhs.getComputation());
            computation.append("if(").append(lhs.getCode()).append(") ").append("goto ").append(true_label).append(END_STMT);
//             tmp1.bool :=.bool 0.bool;
            computation.append(resultTemp).append(resOllirType).append(SPACE).append(ASSIGN).append(resOllirType).append(SPACE).append("0").append(resOllirType).append(END_STMT);
//             goto end_0;
            computation.append("goto ").append(end_label).append(END_STMT);
//            true_0:
//            tmp2.bool :=.bool invokevirtual(c.Arithmetic_and, "p", 1.i32).bool;
//            tmp1.bool :=.bool tmp2.bool;
//            end_0:
//            a.bool :=.bool tmp1.bool;
            computation.append(true_label).append(":").append("\n");
            computation.append(rhs.getComputation());
            computation.append(resultTemp).append(resOllirType).append(SPACE).append(ASSIGN).append(resOllirType).append(SPACE).append(rhs.getCode()).append(END_STMT);
            computation.append(end_label).append(":").append("\n");
            code = resultTemp + resOllirType;
        }
        else {
            // code to compute the children
            computation.append(lhs.getComputation());
            computation.append(rhs.getComputation());
            // Expressions with 2 children that are literal or identifier
            if ((!node.getChild(0).isInstance(BINARY_EXPR)) && (!(node.getChild(1).isInstance(BINARY_EXPR))) && (node.getParent().isInstance(IF_ELSE_STMT) || node.getParent().isInstance(ASSIGN_STMT))) {
                code = lhs.getCode() + SPACE + node.get("op") + OptUtils.toOllirType(resType) + SPACE + rhs.getCode();
                return new OllirExprResult(code, computation);
            }
            code = OptUtils.getTemp() + resOllirType;

            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(resOllirType).append(SPACE)
                    .append(lhs.getCode()).append(SPACE);

            Type type = getExprType(node, table, methodName);
            computation.append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                    .append(rhs.getCode()).append(END_STMT);


        }


        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitIdentifier(JmmNode node, Void unused) {
        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();

        var id = node.get("name");
        Type type = getExprType(node, table, methodName);
        String ollirType = OptUtils.toOllirType(type);

        String code = id + ollirType;

        return new OllirExprResult(code);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
