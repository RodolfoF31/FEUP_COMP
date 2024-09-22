package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2024.analysis.passes.VarDeclarations.allVars;

import static pt.up.fe.comp2024.ast.TypeUtils.*;

public class TypeAnalysis extends AnalysisVisitor {

    private String currentMethod;
    private JmmNode currentMethodNode;

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.IF_ELSE_STMT, this::visitIfElseStmt);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignment);
        addVisit(Kind.RETURN_STMT, this::visitReturns);
        addVisit(Kind.OBJECT_FUNCTION_CALL, this::visitObjectFunctionCall);
        addVisit(Kind.THIS, this::visitThis);

        // addVisit(Kind.FUNCTION_CALL, this::visitFunctionCall);


    }
    private Void visitThis(JmmNode node, SymbolTable table) {
        // Can't use 'this' if the method is static
        if (currentMethodNode.get("isStatic").equals("true")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(node),
                    NodeUtils.getColumn(node),
                    String.format("In method '%s' invalid use of this, method can't be static.", currentMethod),
                    null)
            );
        }
        return null;
    }
    private Void visitObjectFunctionCall(JmmNode node, SymbolTable table) {
        JmmNode caller = node.getChild(0);
        String called_func_name = node.get("name");
        Type caller_type = getExprType(caller, table, currentMethod);

        boolean parameters_ok = true;

        // If the caller object is from type of this class and also extends something then just accept the method exists
        if (caller_type.getName().equals(table.getClassName()) && !table.getSuper().isEmpty()) {return null;}

        // If the caller object is from type of this class
        if (caller_type.getName().equals(table.getClassName())) {
            Type return_type = table.getReturnType(called_func_name);
            if (return_type != null) parameters_ok = checkParameters(node, table, called_func_name, parameters_ok);
            else {
                // When it is from the object is from current class but method doesn't exist
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(node),
                        NodeUtils.getColumn(node),
                        String.format("Function call from '%s' method to function '%s' wasn't found.", currentMethod, called_func_name),
                        null)
                );
                return null;
            }
            if (!parameters_ok) {
                // When the Arguments are incompatible
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(node),
                        NodeUtils.getColumn(node),
                        String.format("Function call from '%s' method to '%s' method number or type of the arguments didn't match.", currentMethod, called_func_name),
                        null)
                );
                return null;
            }
        }

        return null;
    }
    private Boolean checkParameters(JmmNode node, SymbolTable table, String called_func_name, Boolean parameters_ok) {
        List<Symbol> expectedParameters = table.getParameters(called_func_name);
        List<JmmNode> passedArguments = new ArrayList<>();
        var functionParameters = node.getDescendants(Kind.FUNC_PARAMETER);
        if (!functionParameters.isEmpty()) {
            passedArguments = functionParameters.get(0).getChildren();
        }
        // To make sure that there aren't out of index errors
        if (passedArguments.size() < expectedParameters.size()) {
            return false;
        }
        for (var i = 0; i < expectedParameters.size(); i++) {
            // Special case for when the Last argument is a VarArg
            if (i == expectedParameters.size()-1 && isVariableVarArg(expectedParameters.get(i).getName(), table, called_func_name)) {
                // Eval the rest of the children to see if the type matches in all of them to the type of the VarArg
                Type firstType = getExprType(passedArguments.get(i), table, currentMethod); // Get the type of the first argument

                // If it is an array then just accept it as valid to consume the VarArg, in case there aren't more passed arguments, otherwise fail
                if (firstType.isArray() && passedArguments.size()-1 == 0 && firstType.getName().equals(expectedParameters.get(i).getType().getName())) break;

                boolean allRemainingMatch = true;
                for (int j = i + 1; j < passedArguments.size(); j++) {
                    Type currentType = getExprType(passedArguments.get(j), table, currentMethod);
                    if (!currentType.equals(firstType) || currentType.isArray()) {
                        allRemainingMatch = false;
                        break;
                    }
                }
                parameters_ok &= allRemainingMatch && firstType.equals(expectedParameters.get(i).getType());
            }
            else {
                Type expected_type = expectedParameters.get(i).getType();
                Type passed_type = getExprType(passedArguments.get(i), table, currentMethod);
                parameters_ok &= areTypesAssignable(expected_type, passed_type, table);
            }
        }

        return parameters_ok;
    }
    private Void visitIfElseStmt(JmmNode ifStmt, SymbolTable table) {
        var type = TypeUtils.getExprType(ifStmt.getChild(0), table, currentMethod);

        // If the condition is not evaluated to a bool
        if (!type.getName().equals(getBoolTypeName())) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(ifStmt),
                    NodeUtils.getColumn(ifStmt),
                    String.format("If Stmt condition from '%s' method must be boolean, instead got '%s' type.", currentMethod, type),
                    null)
            );
        }
        return null;
    }
    private Void visitWhileStmt(JmmNode whileStmt, SymbolTable table) {
        var type = TypeUtils.getExprType(whileStmt.getChild(0), table, currentMethod);

        // If the condition is not evaluated to a bool
        if (!type.getName().equals(getBoolTypeName())) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(whileStmt),
                    NodeUtils.getColumn(whileStmt),
                    String.format("While Stmt condition from '%s' method must be boolean, instead got '%s' type.", currentMethod, type),
                    null)
            );
        }
        return null;
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        // Used for in every pass know the name of the Method we are in
        currentMethod = method.get("name");
        currentMethodNode = method;

        return null;
    }

    private Void visitAssignment(JmmNode node, SymbolTable table) {
        var left_var = node.getChild(0);
        var right_side = node.getChild(1);

        Type left_type = TypeUtils.getExprType(left_var, table, currentMethod);
        Type right_type = TypeUtils.getExprType(right_side, table, currentMethod);

        // Verify that a field is not being assigned in a static method
        String left_variable_name = left_var.isInstance(Kind.ARRAY_ACCESS) ? left_var.getChild(0).get("name") : left_var.get("name");
        if (TypeUtils.isVariableField(left_variable_name, currentMethod) && currentMethodNode.get("isStatic").equals("true")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(node),
                    NodeUtils.getColumn(node),
                    String.format("In method '%s' invalid assignment to field '%s' from static method.", currentMethod, left_var.get("name")),
                    null)
            );
        }

        if (!areTypesAssignable(left_type, right_type, table)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(node),
                    NodeUtils.getColumn(node),
                    String.format("In method '%s' variable '%s' can't be assigned to the right side because variable has type '%s' and the right expression is of type '%s'", currentMethod, left_var.get("name"), left_type, right_type),
                    null)
            );
        }

        return null;
    }

    private Void visitReturns(JmmNode node, SymbolTable table) {
        Type expectedReturnType = table.getReturnType(currentMethod);
        Type evaluatedReturnType = TypeUtils.getExprType(node.getChild(0), table, currentMethod);

        if (evaluatedReturnType.getName().equals(ERROR_TYPE_NAME)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(node),
                    NodeUtils.getColumn(node),
                    String.format("Return Statment from %s method performing invalid operation", currentMethod),
                    null)
            );
        } else if (!expectedReturnType.equals(evaluatedReturnType)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(node),
                    NodeUtils.getColumn(node),
                    String.format("Return Statment from %s method returning the wrong type, expecting '%s' but instead got '%s'", currentMethod, expectedReturnType, evaluatedReturnType),
                    null)
            );
        }

        return null;
    }
}