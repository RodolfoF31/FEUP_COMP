package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;


public class OptUtils {
    private static int tempNumber = -1;
    private static int tempArrayNumber = -1;
    private static int tempTrueNumber = -1;
    private static int tempEndNumber = -1;
    private static int tempIfBodyNumber = -1;
    private static int tempEndIfNumber = -1;
    private static int tempWhileNumber = -1;
    private static int tempEndWhileNumber = -1;

    public static String getTemp() {
        return getTemp("tmp");
    }

    public static String getTemp(String prefix) {
        return prefix + getNextTempNum();
    }

    public static int getNextTempNum() {
        tempNumber += 1;
        return tempNumber;
    }
    public static String getTempArray() {
        return getTempArray("__varargs_array_");
    }
    public static String getTempArray(String prefix) {
        return prefix + getNextTempArrayNum();
    }
    public static int getNextTempArrayNum() {
        tempArrayNumber += 1;
        return tempArrayNumber;
    }
    public static String getTempTrue() {
        return getTempTrue("true_");
    }
    public static String getTempTrue(String prefix) {
        return prefix + getNextTempTrueNum();
    }
    public static int getNextTempTrueNum() {
        tempTrueNumber += 1;
        return tempTrueNumber;
    }
    public static String getTempEnd() {
        return getTempEnd("end_");
    }
    public static String getTempEnd(String prefix) {
        return prefix + getNextTempEndNum();
    }
    public static int getNextTempEndNum() {
        tempEndNumber += 1;
        return tempEndNumber;
    }
    public static String getTempIfBody() {
        return getTempIfBody("ifbody_");
    }
    public static String getTempIfBody(String prefix) {
        return prefix + getNextTempIfBodyNum();
    }
    public static int getNextTempIfBodyNum() {
        tempIfBodyNumber += 1;
        return tempIfBodyNumber;
    }
    public static String getTempEndIf() {
        return getTempEndIf("endif_");
    }
    public static String getTempEndIf(String prefix) {
        return prefix + getNextTempEndIfNum();
    }
    public static int getNextTempEndIfNum() {
        tempEndIfNumber += 1;
        return tempEndIfNumber;
    }

    public static String getTempWhile() {
        return getTempWhile("whilebody_");
    }
    public static String getTempWhile(String prefix) {
        return prefix + getNextTempWhileNum();
    }
    public static int getNextTempWhileNum() {
        tempWhileNumber += 1;
        return tempWhileNumber;
    }

    public static String getTempEndWhile() {
        return getTempEndWhile("endwhile_");
    }
    public static String getTempEndWhile(String prefix) {
        return prefix + getNextTempEndWhileNum();
    }
    public static int getNextTempEndWhileNum() {
        tempEndWhileNumber += 1;
        return tempEndWhileNumber;
    }
    public static String toOllirType(JmmNode typeNode) {
        String typeName;
        boolean isVarArg = typeNode.getOptional("isVarArg").orElse("false").equals("true");
        if (typeNode.getKind().equals(Kind.ARRAY_TYPE.toString())) {
            typeName = "array" + toOllirType(typeNode.getJmmChild(0).get("name"));
        }
        else if (isVarArg) {
            typeName = "array" + toOllirType(typeNode.get("name"));
        }
        else {
            typeName = typeNode.get("name");
        }

        return toOllirType(typeName);
    }

    public static String toOllirType(Type type) {
        boolean isArray = type.isArray();
        Object isVarArg = type.getOptionalObject("isVarArg").orElse(false);
        if (isArray || Boolean.parseBoolean(isVarArg.toString())) {
            return toOllirType("array" + toOllirType(type.getName()));
        }
        return toOllirType(type.getName());
    }

    private static String toOllirType(String typeName) {
        // Special case for the array
        if (typeName.startsWith("array")) {return "." + typeName;}

        return "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "void" -> "V";

//            default -> throw new NotImplementedException(typeName);
            default -> typeName;
        };
    }

}
