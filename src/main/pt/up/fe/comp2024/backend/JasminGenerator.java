package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final List<Integer> maxStackSizes = new ArrayList<>(List.of(0, 0));
    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateFieldIntruction);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(OpCondInstruction.class, this::generateOpCond);
        generators.put(GotoInstruction.class, this::generateGoTo);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCond);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
        generators.put(ArrayOperand.class, this::generateArrayOperand);
    }

    private void changeStackSize(int size) {
        if (maxStackSizes.isEmpty()) {
            maxStackSizes.add(size);
        } else {
            maxStackSizes.add(maxStackSizes.get(maxStackSizes.size() - 1) + size);
        }
    }

    private String generateArrayOperand(ArrayOperand arrayOperand) {
        StringBuilder code = new StringBuilder();
        var reg = currentMethod.getVarTable().get(arrayOperand.getName()).getVirtualReg();
        var spaceOrUnderScore = reg < 4 ? "_" : " ";
        code.append("aload").append(spaceOrUnderScore).append(reg).append(NL);
        changeStackSize(1);
        code.append(generators.apply(arrayOperand.getIndexOperands().get(0)));
        return code.toString();
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOpInstruction) {
        var code = new StringBuilder();
        code.append(generators.apply(unaryOpInstruction.getOperand()));
        code.append("iconst_1").append(NL);
        changeStackSize(1);
        code.append("ixor").append(NL);
        changeStackSize(-1);

        return code.toString();
    }

    private String generateSingleOpCond(SingleOpCondInstruction singleOpCondInstruction) {
        String code = generators.apply(singleOpCondInstruction.getOperands().get(0)) +
                "ifne " + singleOpCondInstruction.getLabel() + NL;
        changeStackSize(-1);
        return code;
    }

    private String generateGoTo(GotoInstruction gotoInstruction) {
        return "goto " + gotoInstruction.getLabel() + NL;
    }

    private String generateOpCond(OpCondInstruction opCondInstruction) {
        StringBuilder code = new StringBuilder();
        var label = opCondInstruction.getLabel();
        code.append(generators.apply(opCondInstruction.getCondition()));
        String opType = opCondInstruction.getCondition().getOperation().getOpType().name();

        switch (opType) {
            case "LTH" -> {
                code.append("isub").append(NL);
                changeStackSize(-1);
                code.append("iflt ").append(label).append(NL);
                changeStackSize(-1);
            }
            case "GTH" -> {
                code.append("isub").append(NL);
                changeStackSize(-1);
                code.append("ifgt ").append(label).append(NL);
                changeStackSize(-1);
            }
            case "LTE" -> {
                code.append("if_icmple ").append(label).append(NL);
                changeStackSize(-1);
            }
            case "GTE" -> {
                code.append("isub").append(NL);
                changeStackSize(-1);
                code.append("ifge ").append(label).append(NL);
                changeStackSize(-1);
            }
            case "EQ" -> {
                code.append("if_icmpeq ").append(label).append(NL);
                changeStackSize(-1);
            }
            case "NE" -> {
                code.append("if_icmpne ").append(label).append(NL);
                changeStackSize(-1);
            }
            default -> throw new NotImplementedException(opType);
        }

        return code.toString();
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }

    private String generateFieldIntruction(GetFieldInstruction getFieldInstruction) {
        StringBuilder code = new StringBuilder();
        var operands = getFieldInstruction.getOperands();
        code.append(generators.apply(operands.get(0)));
        code.append("getfield ").append(ollirResult.getOllirClass().getClassName()).append("/").append(getFieldInstruction.getField().getName()).append(" ").append(toTypeLetter(getFieldInstruction.getField().getType())).append(NL);

        return code.toString();
    }

    private String generatePutField(PutFieldInstruction putFieldInstruction) {
        StringBuilder code = new StringBuilder();
        var operands = putFieldInstruction.getOperands();

        code.append(generators.apply(operands.get(0)));
        code.append(generators.apply(operands.get(2)));

        // putfield Test/intField I
        code.append("putfield ").append(ollirResult.getOllirClass().getClassName()).append("/").append(putFieldInstruction.getField().getName()).append(" ").append(toTypeLetter(putFieldInstruction.getField().getType())).append(NL);
        changeStackSize(-2);
        return code.toString();
    }
    private String generateCall(CallInstruction callInstruction) {
        var code = new StringBuilder();
        var invocationType = callInstruction.getInvocationType();
        Operand caller = (Operand) callInstruction.getCaller();
        String methodName;
        String returnType;

        switch (invocationType) {
            case invokevirtual -> {
                returnType = toTypeLetter(callInstruction.getReturnType());
                methodName = generators.apply(callInstruction.getMethodName()).replace("\"", "");

                code.append(generators.apply(caller));

                StringBuilder parameters = new StringBuilder();
                for (int i = 0; i < callInstruction.getArguments().size(); i++) {
                    parameters.append(toTypeLetter(callInstruction.getArguments().get(i).getType()));
                    code.append(generators.apply(callInstruction.getArguments().get(i)));
                }

                code.append(String.format("invokevirtual %s/%s(%s)%s%s", ((ClassType) caller.getType()).getName(), methodName, parameters, returnType, NL));
                changeStackSize(-1 - parameters.length());
            }
            case invokespecial -> {
                var reg = currentMethod.getVarTable().get(caller.getName()).getVirtualReg();
                var spaceOrUnderScore = reg < 4 ? "_" : " ";

                code.append(String.format("aload%s%d\ninvokespecial %s/<init>()V%s", spaceOrUnderScore, reg, ((ClassType) caller.getType()).getName(), NL));
            }
            case invokestatic -> {
                methodName = generators.apply(callInstruction.getMethodName()).replace("\"", "");
                String callerName = caller.getName();

                StringBuilder parameters = new StringBuilder();
                for (int i = 0; i < callInstruction.getArguments().size(); i++) {
                    parameters.append(toTypeLetter(callInstruction.getArguments().get(i).getType()));
                    code.append(generators.apply(callInstruction.getArguments().get(i)));
                }
                code.append("invokestatic ").append(callerName).append("/").append(methodName).append("(").append(parameters).append(")").append(toTypeLetter(callInstruction.getReturnType()));
                changeStackSize(-parameters.length());
            }
            case NEW -> {
                // Array
                if (caller.getName().equals("array")) {
                    code.append(generators.apply(callInstruction.getArguments().get(0)));
                    code.append("newarray int").append(NL);
                    changeStackSize(0);
                    break;
                }
                // ClassType
                returnType = ((ClassType) caller.getType()).getName();
                code.append("new ").append(returnType).append(NL);
                changeStackSize(1);
            }
            case arraylength -> {
                code.append(generators.apply(caller));
                code.append("arraylength").append(NL);
                changeStackSize(0);
            }
        }

        return code.toString();
    }

    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        var classAcessModifiers = classUnit.getClassAccessModifier().equals(AccessModifier.DEFAULT) ? "public" : classUnit.getClassAccessModifier();

        code.append(".class ").append(classAcessModifiers).append(" ").append(className).append(NL).append(NL);
        code.append(".super java/lang/Object").append(NL);

        // Add field declaration
        for (Field field: classUnit.getFields()) {
            String f = String.format(".field %s %s%s", field.getFieldName(), toTypeLetter(field.getFieldType()), NL);
            code.append(f);
        }

        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial java/lang/Object/<init>()V
                    return
                .end method
                """;
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }

    private String generateMethod(Method method) {

        // set method
        currentMethod = method;

        maxStackSizes.clear();

        var code = new StringBuilder();

        // calculate modifier
        String modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";
        if (method.isStaticMethod()) modifier += "static ";

        var methodName = method.getMethodName();

        // Get method parameters
        Type returnType = method.getReturnType();
        List<Element> parameters = method.getParams();

        code.append("\n.method ").append(modifier).append(methodName);
        // Append parameters
        code.append("(");
        for (Element parameter : parameters) {
            code.append(toTypeLetter(parameter.getType()));
        }
        code.append(")");
        // Add the return type at the end
        code.append(toTypeLetter(returnType));
        code.append(NL);


        StringBuilder withoutLimits = new StringBuilder();

        for (var inst : method.getInstructions()) {
            var label = method.getLabels(inst);
            if (!label.isEmpty()) {
                withoutLimits.append(method.getLabels(inst).get(0)).append(":").append(NL);
            }
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            withoutLimits.append(instCode);
        }

        withoutLimits.append(".end method\n");


        // Add limits
        if (maxStackSizes.isEmpty()) {
            code.append(TAB).append(".limit stack 0").append(NL);
        } else {
            code.append(TAB).append(".limit stack ").append(Collections.max(maxStackSizes)).append(NL);
        }

        // Calculate locals
        int numLocals = currentMethod.isStaticMethod() ? 0 : 1;
        for (var value: currentMethod.getVarTable().values()) {
            if (value.getScope().equals(VarScope.LOCAL) || value.getScope().equals(VarScope.PARAMETER)) {
                if (value.getVarType().getTypeOfElement().equals(ElementType.THIS)) continue;
                numLocals++;
            }
        }
        code.append(TAB).append(".limit locals ").append(numLocals).append(NL);

        // unset method
        currentMethod = null;

        code.append(withoutLimits);

        return code.toString();
    }

    private String toTypeLetter(Type type) {
        ElementType elementType = type.getTypeOfElement();
        return switch (elementType) {
            case INT32 -> "I";
            case BOOLEAN -> "Z";
            case ARRAYREF -> "[" + toTypeLetter(((ArrayType)type).getElementType());
            case OBJECTREF, CLASS, THIS -> null;
            case STRING -> "Ljava/lang/String;";
            case VOID -> "V";
        };
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // store value in the stack in destination
        var rhs = assign.getRhs();
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        // get register
        var assign_var_name = operand.getName();
        var reg = currentMethod.getVarTable().get(assign_var_name).getVirtualReg();
        var spaceOrUnderScore = reg < 4 ? "_" : " ";


        // Assign to array
        if (operand instanceof ArrayOperand) {
            code.append(generators.apply(operand));
            code.append(generators.apply(assign.getRhs()));
            code.append("iastore").append(NL);
            changeStackSize(-3);
            return code.toString();
        }
        else if (rhs instanceof BinaryOpInstruction) {
            var binaryOp = (BinaryOpInstruction) rhs;
            var left_operand = binaryOp.getLeftOperand();
            var right_operand = binaryOp.getRightOperand();

            boolean isLeftLiteralAndRightNameMatches = left_operand.isLiteral() && right_operand instanceof Operand && ((Operand) right_operand).getName().equals(assign_var_name) && left_operand.getType().getTypeOfElement().equals(ElementType.INT32);
            boolean isRightLiteralAndLeftNameMatches = right_operand.isLiteral() && left_operand instanceof Operand && ((Operand) left_operand).getName().equals(assign_var_name) && left_operand.getType().getTypeOfElement().equals(ElementType.INT32);

            if (isLeftLiteralAndRightNameMatches) {
                code.append(generateOperationCode(binaryOp, reg, (LiteralElement) left_operand));
                return code.toString();
            }
            else if (isRightLiteralAndLeftNameMatches) {
                code.append(generateOperationCode(binaryOp, reg, (LiteralElement) right_operand));
                return code.toString();
            }
        }
        code.append(generators.apply(rhs));

        switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN:
                code.append("istore").append(spaceOrUnderScore).append(reg).append(NL);
                changeStackSize(-1);
                break;
            case ARRAYREF:
            case OBJECTREF:
                code.append("astore").append(spaceOrUnderScore).append(reg).append(NL);
                changeStackSize(-1);
                break;
            default:
                System.err.println("ERROR: FAILED TO GENERATE ASSIGNMENT");
                break;
        }

        return code.toString();
    }

    private String generateOperationCode(BinaryOpInstruction binaryOp, int reg, LiteralElement operand) {
        StringBuilder code = new StringBuilder();
        if (binaryOp.getOperation().getOpType() == OperationType.ADD) {
            code.append("iinc").append(" ").append(reg).append(" ").append(operand.getLiteral()).append(NL);
        } else if (binaryOp.getOperation().getOpType() == OperationType.SUB) {
            code.append("iinc").append(" ").append(reg).append(" -").append(operand.getLiteral()).append(NL);
        }
        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        // When it is an array acess
        if (singleOp.getSingleOperand() instanceof ArrayOperand) {
            String code = generators.apply(singleOp.getSingleOperand()) +
                    "iaload" + NL;
            changeStackSize(1);
            return code;
        }


        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        if (literal.getType().getTypeOfElement().equals(ElementType.STRING)) {
            changeStackSize(1);
            return literal.getLiteral();
        }
        if (literal.getType().getTypeOfElement().equals(ElementType.BOOLEAN)) {
            changeStackSize(1);
            return literal.getLiteral().equals("1") ? "iconst_1" + NL : "iconst_0" + NL;
        }
        if (literal.getType().getTypeOfElement().equals(ElementType.INT32)){
            if (Integer.parseInt(literal.getLiteral()) >= -1 && Integer.parseInt(literal.getLiteral()) <= 5) {
                changeStackSize(1);
                return "iconst_" + literal.getLiteral() + NL;
            }
            else if (Integer.parseInt(literal.getLiteral()) >= -128 && Integer.parseInt(literal.getLiteral()) <= 127) {
                changeStackSize(1);
                return "bipush " + literal.getLiteral() + NL;
            }
            else if (Integer.parseInt(literal.getLiteral()) >= -32768 && Integer.parseInt(literal.getLiteral()) <= 32767) {
                changeStackSize(1);
                return "sipush " + literal.getLiteral() + NL;
            }
        }
        changeStackSize(1);
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        var type = operand.getType().getTypeOfElement();
        var spaceOrUnderScore = reg < 4 ? "_" : " ";
        changeStackSize(1);

        return switch (type) {
            case THIS -> "aload_0" + NL;
            case OBJECTREF, CLASS, STRING, ARRAYREF -> "aload" + spaceOrUnderScore + reg + NL;
            default -> "iload" + spaceOrUnderScore + reg + NL;
        };

    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        switch (binaryOp.getOperation().getOpType()) {
            case ADD -> {
                changeStackSize(-1);
                code.append("iadd\n");
            }
            case SUB -> {
                changeStackSize(-1);
                code.append("isub\n");
            }
            case MUL -> {
                changeStackSize(-1);
                code.append("imul\n");
            }
            case DIV -> {
                changeStackSize(-1);
                code.append("idiv\n");
            }
            case LTH, GTE -> {
            }
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        }


        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        ElementType returnTypeElement = returnInst.getReturnType().getTypeOfElement();
        boolean isVoid = returnTypeElement.equals(ElementType.VOID);
        if (isVoid) return "return";

        code.append(generators.apply(returnInst.getOperand()));

        // Adjust the stack size
        changeStackSize(-1); // One value is popped from the stack for the return

        code.append("ireturn").append(NL);

        return code.toString();
    }
}
