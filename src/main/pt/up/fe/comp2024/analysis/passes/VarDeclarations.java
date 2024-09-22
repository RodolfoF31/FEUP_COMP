package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.ArrayList;
import java.util.List;

public class VarDeclarations extends AnalysisVisitor {
    private String currentMethod;
    public static List<VarTypeScope> allVars = new ArrayList<>();

    public static class VarTypeScope {
        public String var;
        public String scope;
        public String method_name;
        public Type type;
        public boolean isVarArg = false;


        public VarTypeScope(String var, String scope, String method_name, Type type) {
            this.var = var;
            this.scope = scope;
            this.type = type;
            this.method_name = method_name;
        }
        public VarTypeScope(String var, String scope, String method_name, Type type, boolean isVarArg) {
            this.var = var;
            this.scope = scope;
            this.type = type;
            this.method_name = method_name;
            this.isVarArg = isVarArg;
        }

        @Override
        public String toString() {
            return "Variable: " + var + ", Scope: " + scope + ", Method: " + method_name + ", Type: " + type.getName() + ", IsArray: " + type.isArray();
        }

        public String getName() {
            return this.var;
        }
        public String getScope() {
            return this.scope;
        }
    }

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        // Fill the VarTypeScope with each declared variable
        addVisit(Kind.VAR_DECL, this::visitVarDeclaration);
        addVisit(Kind.PARAMETER, this::visitParameters);
    }

    private Void visitParameters(JmmNode node, SymbolTable table) {
        var varRefName = node.get("name");
        boolean isVarArg = node.getChildren().get(0).get("isVarArg").equals("true");

        if (currentMethod != null) {
            // Var is a parameter
            for (var param : table.getParameters(currentMethod)) {
                if (param.getName().equals(varRefName)) {
                    allVars.add(new VarTypeScope(param.getName(), "parameter", currentMethod, param.getType(), isVarArg));
                }
            }
        }

        return null;
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");

        // Verify the return type to make sure it is not a VarArg
        if (method.getChildren().get(0).get("isVarArg").equals("true")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(method),
                    NodeUtils.getColumn(method),
                    String.format("Return type of declaration from %s method can't be a VarArg", currentMethod),
                    null)
            );
            return null;
        }

        // Get method parameters and verify if there is only one VarArg and if it is the last of the paremeters
        var parameters = method.getChildren().get(1).getChildren("Parameter");
        for (int i = 0; i < parameters.size(); i++) {
            boolean isVarArg = parameters.get(i).getChild(0).get("isVarArg").equals("true");

            if (isVarArg && i != parameters.size()-1) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(method),
                        NodeUtils.getColumn(method),
                        String.format("Method parameter %s of declaration from %s method can't be a VarArg because it isn't the last parameter", parameters.get(i).get("name"), currentMethod),
                        null)
                );
                return null;
            }
        }


        return null;
    }
    private Void visitVarDeclaration(JmmNode node, SymbolTable table) {

        var varRefName = node.get("name");
        boolean isVarArg = node.getChildren().get(0).get("isVarArg").equals("true");
        // Var is a declared variable
        if (currentMethod != null) {
            for (var varDecl : table.getLocalVariables(currentMethod)) {
                if (varDecl.getName().equals(varRefName)) {
                    if (isVarArg) {
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(node),
                                NodeUtils.getColumn(node),
                                String.format("Variable declaration from %s method defining VarArg as local variable", currentMethod),
                                null)
                        );
                        return null;
                    }
                    allVars.add(new VarTypeScope(varDecl.getName(), "local", currentMethod, varDecl.getType()));
                }
            }
        }

        // Var is a field
        for (var param : table.getFields()) {
            if (param.getName().equals(varRefName)) {
                if (isVarArg) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(node),
                            NodeUtils.getColumn(node),
                            "Variable declaration from method defining VarArg as field variable",
                            null)
                    );
                    return null;
                }

                allVars.add(new VarTypeScope(param.getName(), "field", "", param.getType()));
            }
        }

        return null;
    }

}