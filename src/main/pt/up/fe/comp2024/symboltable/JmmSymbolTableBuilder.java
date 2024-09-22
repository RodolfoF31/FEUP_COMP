package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.METHOD_DECL;
import static pt.up.fe.comp2024.ast.Kind.VAR_DECL;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {
        // Still missing check for more than one class
        List<JmmNode> classDeclarations = root.getChildren("ClassDecl");
        // Check if it getts only one class declaration
        JmmNode classDecl = (classDeclarations.size() == 1) ? classDeclarations.get(0) : null;
        SpecsCheck.checkArgument(classDecl != null, () -> "It is expected to have a single declaration!");

        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");

        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        var imports = buildImports(root);
        var fields = buildFields(classDecl);
        var superName = buildSuper(classDecl);

        return new JmmSymbolTable(className, methods, returnTypes, params, locals, imports, fields, superName);
    }
    private static List<String> buildImports(JmmNode program) {
        List<String> imports = new ArrayList<>();
        program.getChildren("ImportStmt").forEach(import_ -> {
            List<Object> values = import_.getObjectAsList("value");
            List<String> stringValues = new ArrayList<>();
            values.forEach(value -> stringValues.add(value.toString()));
            String importValue = String.join(".", stringValues);
            imports.add(importValue);
        });
//        System.out.println("Imports" + imports);
        return imports;
    }
    private static List<Symbol> buildFields(JmmNode classDecl) {
        List<JmmNode> variableNodes = classDecl.getChildren("VarDeclaration");
        List<Symbol> symbols = new ArrayList<>();
        variableNodes.forEach(variable -> {
            JmmNode typeNode = variable.getChildren("Type").get(0);
            String name;
            Type variableType;
            if (Objects.equals(typeNode.toString(), "ArrayType")) {
                variableType = new Type(typeNode.getChildren("Type").get(0).get("name"), true);
                variableType.putObject("isVarArg", false);
            }
            else {
                variableType = new Type(typeNode.get("name"), false);
                variableType.putObject("isVarArg", Boolean.parseBoolean(typeNode.get("isVarArg")));
            }
            name = variable.get("name");
            symbols.add(new Symbol(variableType, name));
        }
        );
//        System.out.println("buildFields: " + symbols);
        return symbols;
    }
    private static String buildSuper(JmmNode classDecl) {
        Optional<String> superName = classDecl.getOptional("extendedClass");
        return superName.orElse("");
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL)
                .forEach(method -> {
                    String methodName = method.get("name");
                    JmmNode typeNode = method.getChildren("Type").get(0);

                    if (Objects.equals(typeNode.toString().split(" ")[0], "ArrayType")) {
                        var typeOfArray = typeNode.getChildren("Type").get(0).get("name");
                        map.put(methodName, new Type(typeOfArray, true));
                    }
                    else {
                        map.put(methodName, new Type(typeNode.get("name"), false));
                    }
                });

//        System.out.println("buildReturnTypes" + map);
        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL)
                .forEach(method -> {
                    List<Symbol> parametersList = new ArrayList<>();
                    JmmNode methodParameters = method.getJmmChild(1);
                    var parameterNodes = methodParameters.getChildren("Parameter");
//                    System.out.println(parameterNodes);
                    parameterNodes.forEach(parameter -> {
                        JmmNode typeNode = parameter.getChildren("Type").get(0);
                        String name;
                        Type variableType;
                        if (Objects.equals(typeNode.toString().split(" ")[0], "ArrayType")) {
                            variableType = new Type(typeNode.getChildren("Type").get(0).get("name"), true);
                            variableType.putObject("isVarArg", false);
                        }
                        else {
                            variableType = new Type(typeNode.get("name"), false);
                            variableType.putObject("isVarArg", Boolean.parseBoolean(typeNode.get("isVarArg")));
                        }
                        name = parameter.get("name");
                        parametersList.add(new Symbol(variableType, name));
                    });
                    map.put(method.get("name"), parametersList);
                });

//        System.out.println("buildParams" + map);
        return map;
    }


    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL)
                .forEach(method -> map.put(method.get("name"), getLocalsList(method)));

//        System.out.println("buildLocals" + map);
        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {
        //        System.out.println("buildMethods: " + methods);
        return classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();
    }

    private static List<Symbol> getLocalsList(JmmNode methodDecl) {

        return methodDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> {
                    JmmNode typeNode = varDecl.getChildren("Type").get(0);
                    String name;
                    Type variableType;

                    if (Objects.equals(typeNode.toString().split(" ")[0], "ArrayType")) {
                        variableType = new Type(typeNode.getChildren("Type").get(0).get("name"), true);
                        variableType.putObject("isVarArg", false);
                    }
                    else {
                        variableType = new Type(typeNode.get("name"), false);
                        variableType.putObject("isVarArg", Boolean.parseBoolean(typeNode.get("isVarArg")));
                    }
                    name = varDecl.get("name");

                    return new Symbol(variableType, name);
                })
                .toList();
    }

}
