package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static pt.up.fe.comp2024.analysis.passes.VarDeclarations.allVars;

public class TypeUtils {
    private static final String INT_TYPE_NAME = "int";
    private static final String BOOL_TYPE_NAME = "boolean";
    private static final String DOUBLE_TYPE_NAME = "double";
    private static final String FLOAT_TYPE_NAME = "float";
    private static final String STRING_TYPE_NAME = "String";
    private static final String VOID_TYPE_NAME = "void";
    public static final String ERROR_TYPE_NAME = "error";
    public static final String NEW_ARRAY_TYPE_NAME = "newArray";
    public static final String NEW_CLASS_TYPE_NAME = "newClass";
    public static final String ARRAY_INITIALIZATION = "arrayInit";
    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }
    public static String getBoolTypeName() {
        return BOOL_TYPE_NAME;
    }
    public static String getVoidTypeName() { return VOID_TYPE_NAME;}
    public static String getDoubleTypeName() {
        return DOUBLE_TYPE_NAME;
    }
    public static String getFloatTypeName() {
        return FLOAT_TYPE_NAME;
    }
    public static String getStringTypeName() {
        return STRING_TYPE_NAME;
    }


    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table, String currentMethod) {
        // TODO: Simple implementation that needs to be expanded

        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr, table, currentMethod);
            case INTEGER_LITERAL -> new Type(INT_TYPE_NAME, false);
            case LENGTH -> new Type(INT_TYPE_NAME, false);
            case BOOL_VALUE -> new Type(BOOL_TYPE_NAME, false);
            case IDENTIFIER -> getIdentifierType(expr, table, currentMethod);
            case THIS -> new Type(table.getClassName(), false);
            case PARENTESIS -> getExprType(expr.getChild(0), table, currentMethod);
            case SIMPLE_ARRAY -> getSimpleArrayType(expr, table, currentMethod);
            case OBJECT_FUNCTION_CALL -> getObjectFunctionCallType(expr, table, currentMethod);
            case ARRAY_ACCESS -> getArrayAccessType(expr, table, currentMethod);
            case NEG_OPERATOR -> getNegationType(expr, table, currentMethod);
            case NEW_ARRAY -> new Type(NEW_ARRAY_TYPE_NAME, true);
            case NEW_CLASS -> new Type(expr.get("name"), false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }

    private static Type getNegationType(JmmNode expr, SymbolTable table, String currentMethod) {
        Type type = getExprType(expr.getChild(0), table, currentMethod);
        if (type.equals(new Type(BOOL_TYPE_NAME, false))) return type;
        return new Type(ERROR_TYPE_NAME, false);
    }

    private static Type getObjectFunctionCallType(JmmNode expr, SymbolTable table, String currentMethod) {
        // TODO
        var object = expr.getChild(0);
        var object_type = getExprType(object, table, currentMethod);
        var this_class = table.getClassName();
        String function_name = expr.get("name");

        // If caller object is an imported type then just assume that it returns the correct type for the current method
        if (isImported(object_type, table)) return table.getReturnType(currentMethod);
        if (isImportedPackage(expr.getChild(0).get("name"), table)) return new Type(VOID_TYPE_NAME, false);

        // Check to see if it is from this class or from imported class
        // From this class
        if (this_class.equals(object_type.getName())) {
            // Verify if that method is present and if so check the return type for a match
            var method = table.getMethods();
            if (method.contains(function_name)) {
                return table.getReturnType(function_name);
            }
        }

        return new Type(ERROR_TYPE_NAME, false);
    }

    private static Type getArrayAccessType(JmmNode expr, SymbolTable table, String currentMethod) {
        // TODO
        Type type_of_variable = getExprType(expr.getChild(0), table, currentMethod);
        Type inner_part_type = getExprType(expr.getChild(1), table, currentMethod);
        boolean isVariableVarArg = isIdentifierVarArg(expr.getChild(0), currentMethod);

        // Array access must be possible if it is an array or a VarArg and the inner part is evaluated to an int
        if ((type_of_variable.isArray() || isVariableVarArg) && inner_part_type.equals(new Type(INT_TYPE_NAME, false))) {
            return new Type(type_of_variable.getName(), false);
        }
        return new Type(ERROR_TYPE_NAME, false);
    }

    // Verify if all types match inside the array
    private static Type getSimpleArrayType(JmmNode expr, SymbolTable table, String currentMethod) {
        List<JmmNode> childrens = expr.getChildren();
        int count_ints = 0;

        for (JmmNode children : childrens) {
            if (children.getKind().equals(Kind.INTEGER_LITERAL.toString())) {
                count_ints++;
            }
        }
        if (count_ints == childrens.size()) return new Type(ARRAY_INITIALIZATION, true);

        return new Type(ERROR_TYPE_NAME, false);
    }

    private static Type getIdentifierType(JmmNode identifier, SymbolTable table, String currentMethod) {
        // To make sure that is getting the variable from the correct scope with the right precedence (local > parameter > field)
        Type currentType = new Type(ERROR_TYPE_NAME, false);
        boolean isLocalOrParameter = false;
        for (var elem: allVars) {
            if (elem.var.equals(identifier.get("name"))) {
                String method = elem.method_name;
                // It is a field
                if (!isLocalOrParameter && method.isEmpty()) currentType = elem.type;
                // It is either a local or a parameter
                else if (method.equals(currentMethod)) {
                    currentType = elem.type;
                    isLocalOrParameter = true;
                }
            }
        }
        if (isImportedPackage(identifier.get("name"), table)) return new Type("Imported", false);

        return currentType;
    }
    public static boolean isIdentifierVarArg(JmmNode identifier, String currentMethod) {
        for (var elem: allVars) {
            if (elem.var.equals(identifier.get("name"))) {
                if (elem.method_name.equals(currentMethod)) {
                    return elem.isVarArg;
                }
            }
        }
        return false;
    }
    public static boolean isVariableVarArg(String variable, SymbolTable table, String currentMethod) {
        for (var elem: allVars) {
            if (elem.var.equals(variable)) {
                if (elem.method_name.equals(currentMethod)) {
                    return elem.isVarArg;
                }
            }
        }
        return false;
    }
    public static boolean isVariableField(String variable, String currentMethod) {
        boolean result = false;
        for (var elem: allVars) {
            if (elem.var.equals(variable)) {
                if (elem.method_name.equals(currentMethod)) return false;
                result = elem.scope.equals("field");
            }
        }
        return result;
    }

    private static Type getBinExprType(JmmNode binaryExpr, SymbolTable table, String currentMethod) {

        String operator = binaryExpr.get("op");
        var left_type = getExprType(binaryExpr.getChild(0), table, currentMethod);
        var right_type = getExprType(binaryExpr.getChild(1), table, currentMethod);

        return switch (operator) {
            case "+", "*", "-", "/" -> {
                if (left_type.equals(right_type) && !left_type.equals(new Type(BOOL_TYPE_NAME,false)) && !right_type.equals(new Type(BOOL_TYPE_NAME,false)) && !left_type.isArray() && !right_type.isArray()) {
                    yield left_type;
                }
                yield new Type(ERROR_TYPE_NAME, false);
            }
            case "&&", "||" -> {
                if (left_type.equals(right_type) && left_type.equals(new Type(BOOL_TYPE_NAME,false)) && !left_type.isArray() && !right_type.isArray()) {
                    yield left_type;
                }
                yield new Type(ERROR_TYPE_NAME, false);
            }
            case "<" -> {
                if (left_type.equals(right_type) && !left_type.equals(new Type(BOOL_TYPE_NAME,false)) && !right_type.equals(new Type(BOOL_TYPE_NAME,false)) && !left_type.isArray() && !right_type.isArray()) {
                    yield new Type(BOOL_TYPE_NAME, false);
                }
                yield new Type(ERROR_TYPE_NAME, false);
            }
            default -> throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }

    /**
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType, SymbolTable table) {

        // Verify if the two types are imported types. If they are accept assignment
        if (isImported(sourceType, table) && isImported(destinationType, table)) return true;

        // Verify if it is the current class and if it is, if it extends the other one
        if (thisClassExtends(sourceType, destinationType, table)) return true;

        // Verify array initialization
        if (sourceType.isArray() && destinationType.isArray()) return true;

        return sourceType.getName().equals(destinationType.getName());
    }
    private static boolean thisClassExtends(Type sourceType, Type destinationType, SymbolTable table) {
       return (table.getClassName().equals(sourceType.getName()) && table.getSuper().equals(destinationType.getName()) || table.getClassName().equals(destinationType.getName()) && table.getSuper().equals(sourceType.getName()));
    }

    private static boolean isImported(Type type, SymbolTable table) {
        var imports = table.getImports();
        return imports.contains(type.getName());
    }

    public static boolean isImportedPackage(String caller, SymbolTable table) {
        var imports = table.getImports();
        return imports.contains(caller);
    }

}
