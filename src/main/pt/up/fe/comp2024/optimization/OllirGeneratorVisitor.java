package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;
import static pt.up.fe.comp2024.ast.TypeUtils.getExprType;
import static pt.up.fe.comp2024.optimization.OptUtils.toOllirType;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {
        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_STMT, this::visitImports);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAMETER, this::visitParam);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(IF_ELSE_STMT, this::visitIfElseStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitWhileStmt(JmmNode jmmNode, Void unused) {
        JmmNode condition = jmmNode.getChild(0);
        JmmNode body = jmmNode.getChild(1);
        var conditionResult = exprVisitor.visit(condition);

        StringBuilder code = new StringBuilder();
        String tempWhile = OptUtils.getTempWhile();
        String tempEndWhile = OptUtils.getTempEndWhile();

//      if (i.i32 <.bool a.i32) goto whilebody_1;
//      goto endwhile_1;
        code.append(conditionResult.getComputation());
        code.append("if(").append(conditionResult.getCode()).append(") goto ").append(tempWhile).append(END_STMT);
        code.append("goto ").append(tempEndWhile).append(END_STMT);

//      whilebody_1:
        code.append(tempWhile).append(":\n");

        for (var child : body.getChildren()) {
            code.append(visit(child));
        }

//      if (i.i32 <.bool a.i32) goto whilebody_1;
//      endwhile_1:
        code.append("if(").append(conditionResult.getCode()).append(") goto ").append(tempWhile).append(END_STMT);
        code.append(tempEndWhile).append(":\n");


        return code.toString();
    }

    private String visitIfElseStmt(JmmNode jmmNode, Void unused) {
        JmmNode condition = jmmNode.getChild(0);
        JmmNode ifBody = jmmNode.getChild(1);
        JmmNode elseBody = jmmNode.getChild(2);

        StringBuilder code = new StringBuilder();
        String tempIfBody = OptUtils.getTempIfBody();
        String tempEndIf = OptUtils.getTempEndIf();

        var conditionResult = exprVisitor.visit(condition);
        // tmp0.bool :=.bool a.i32 <.bool b.i32;
        // if (tmp0.bool) goto if_body_0;
        code.append(conditionResult.getComputation());
        code.append("if (").append(conditionResult.getCode()).append(") goto ").append(tempIfBody).append(END_STMT);

        // Process the else
        for (var child : elseBody.getChildren()) {
            code.append(visit(child));
        }

        code.append("goto ").append(tempEndIf).append(END_STMT);
        code.append(tempIfBody).append(":\n");

        // Process the if body
        for (var child : ifBody.getChildren()) {
            code.append(visit(child));
        }

        code.append(tempEndIf).append(":\n");



        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        // Call to the object function without assignment
        var exprResult = exprVisitor.visit(node.getChild(0));

        return exprResult.getComputation() + exprResult.getCode();
    }
    private String visitImports(JmmNode node, Void unused) {
        String import_unfiltered = node.get("value");
        import_unfiltered = import_unfiltered.substring(1, import_unfiltered.length() - 1).replaceAll("\\s", "");
        String[] separated_import = import_unfiltered.split(",");

        return "import " + String.join(".", separated_import) + END_STMT;
    }
    private String visitAssignStmt(JmmNode node, Void unused) {
        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        var dest_node = node.getJmmChild(0);
        var operation_node = node.getJmmChild(1);

        var lhs = exprVisitor.visit(dest_node);
        var rhs = exprVisitor.visit(operation_node);

        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(lhs.getComputation());
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        Type thisType = getExprType(node.getJmmChild(0), table, methodName);
        String typeString = toOllirType(thisType);


        code.append(lhs.getCode());
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }

    private String visitReturn(JmmNode node, Void unused) {

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation());
        code.append("ret");
        code.append(toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }

    private String visitParam(JmmNode node, Void unused) {

        var typeCode = toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        return id + typeCode;
    }

    private String visitVarDecl(JmmNode node, Void unused) {
        boolean isField = node.getAncestor(METHOD_DECL).isEmpty();
        StringBuilder code = new StringBuilder();

        if (isField) {
            String varName = node.get("name");
            String varType = toOllirType(node.getJmmChild(0));

            code.append(".field ");
            if (node.get("isPrivate").equals("true")) code.append("private ");
            else code.append("public ");

            code.append(varName);
            code.append(varType);
            code.append(END_STMT);
        }


        return code.toString();
    }

    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");
        boolean isStatic = NodeUtils.getBooleanAttribute(node, "isStatic", "false");

        if (isPublic) {
            code.append("public ");
        }

        if (isStatic) {
            code.append("static ");
        }

        // name
        var name = node.get("name");
        code.append(name);


        // param
        var parameters = node.getDescendants(PARAMETER);
        var paramCode = new StringBuilder();
        for (JmmNode parameter: parameters) {
            paramCode.append(visit(parameter)).append("," + SPACE);
        }
        // Remove unecessary comma
        if (paramCode.length() >= 2) paramCode.delete(paramCode.length() - 2, paramCode.length());

        code.append("(").append(paramCode).append(")");

        // type
        var retType = toOllirType(node.getJmmChild(0));
        code.append(retType);
        code.append(L_BRACKET);


        // rest of its children stmts
        boolean hasReturn = false;
        var afterParam = 1;
        if (!parameters.isEmpty()) afterParam = 2;


        for (int i = afterParam; i < node.getNumChildren(); i++) {
            var child = node.getJmmChild(i);
            if (child.getKind().equals(RETURN_STMT.toString())) hasReturn = true;
            var childCode = visit(child);
            code.append(childCode);
        }
        if (!hasReturn) {code.append("ret.V" + END_STMT);}

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }

    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());
        if (!table.getSuper().isEmpty()) {
            code.append(" extends ").append(table.getSuper());
        }

        code.append(L_BRACKET);

        code.append(NL);
        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }


    private String visitProgram(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        for (var importNode : node.getChildren(IMPORT_STMT)) {
            code.append(visit(importNode));
        }

        node.getChildren().stream()
                .filter(child -> !IMPORT_STMT.check(child))
                .map(this::visit)
                .forEach(code::append);

        System.out.println("Final Ollir: \n");
        System.out.println(code);
        return code.toString();
    }


    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
