import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {
    private static int tabc = 0;

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: GenerateAst <output directory>");
            System.exit(64);
        }
        String outputDir = args[0];

        defineAst(outputDir, "Expr", Arrays.asList(
            "Assign   : Token name, Expr value",
            "Binary   : Expr left, Token operator, Expr right",
            "Call     : Expr callee, Token paren, List<Expr> arguments",
            "Grouping : Expr expression",
            "Literal  : Object value",
            "Logical  : Expr left, Token operator, Expr right",
            "Ternary  : Expr condition, Expr thenBranch, Expr elseClause",
            "Unary    : Token operator, Expr right",
            "Var      : Token name"
        ));

        defineAst(outputDir, "Stmt", Arrays.asList(
            "Block      : List<Stmt> statements",
            "Break      : ",
            "Expression : Expr expression",
            "Function   : Token name, List<Token> params, List<Stmt> body",
            "If         : Expr condition, Stmt thenBranch, Stmt elseBranch",
            "Print      : Expr expression",
            "Return     : Token keyword, Expr value",
            "Var        : Token name, Expr initializer",
            "While      : Expr condition, Stmt body"
        ));
    }

    private static void defineAst(String outputDir, String baseName, List<String> types) throws IOException {
        String path = outputDir + "/" + baseName + ".java";
        PrintWriter writer = new PrintWriter(path, "UTF-8");
        
        writer.printf("package jlox;\n\n");
        writer.printf("import java.util.List;\n\n");
        writer.printf("abstract class %s {\n", baseName);
        tabc++;

        defineVisitor(writer, baseName, types);

        for (String type : types) {
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim();
            defineType(writer, baseName, className, fields);
        }

        writer.printf("%sabstract <R> R accept(Visitor<R> visitor);\n", tabs());

        tabc--;
        writer.printf("}\n");
        writer.close();
    }

    private static void defineVisitor(PrintWriter writer, String baseName, List<String> types) {
        writer.printf("%sinterface Visitor<R> {\n", tabs());
        tabc++;

        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.printf("%sR visit(%s %s);\n", tabs(), typeName, baseName.toLowerCase());
        }

        tabc--;
        writer.printf("%s}\n\n", tabs());
    }

    private static void defineType(PrintWriter writer, String baseName, String className, String fieldList) {
        writer.printf("%sstatic class %s extends %s {\n", tabs(),  className, baseName);
        tabc++;
        
        String[] fields = fieldList.length() == 0 ? new String[0] : fieldList.split(", ");
        for (String field : fields) {
            writer.printf("%sfinal %s;\n", tabs(), field);
        }

        writer.printf("\n%s%s(%s) {\n", tabs(), className, fieldList);
        tabc++;

        for (String field : fields) {
            String name = field.split(" ")[1];
            writer.printf("%sthis.%s = %s;\n", tabs(), name, name);
        }

        tabc--;
        writer.printf("%s}\n\n", tabs());

        writer.printf("%s@Override\n", tabs());
        writer.printf("%s<R> R accept(Visitor<R> visitor) {\n", tabs());
        tabc++;
        writer.printf("%sreturn visitor.visit(this);\n", tabs());
        tabc--;
        writer.printf("%s}\n", tabs());
        
        tabc--;
        writer.printf("%s}\n\n", tabs());
    }

    private static String tabs() {
        return "\t".repeat(tabc);
    }
}
