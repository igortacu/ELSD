package elsd;

import elsd.ast.ASTBuilder;
import elsd.ast.ASTDotExporter;
import elsd.ast.ASTNode;
import elsd.ast.ASTPrinter;
import elsd.ast.ASTTreeViewer;
import elsd.generated.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.gui.TreeViewer;

import javax.swing.*;
import java.io.IOException;
import java.io.FileWriter;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// ELSD compiler entry point — lex, parse, and optionally render the AST
public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java elsd.Main [--tokens] [--ast] [--graph] [--ast-gui] [--gui] <file.elsd>");
            System.err.println();
            System.err.println("Options:");
            System.err.println("  --tokens   Print the token stream");
            System.err.println("  --ast      Build and print the Abstract Syntax Tree");
            System.err.println("  --graph    Export AST as graph image (via graphviz)");
            System.err.println("  --ast-gui  Open the AST graphical viewer (Swing)");
            System.err.println("  --gui      Open the ANTLR parse-tree GUI (requires display)");
            System.exit(1);
        }

        boolean showTokens = false;
        boolean showAst = false;
        boolean showGraph = false;
        boolean showAstGui = false;
        boolean showGui = false;
        String filePath = null;

        for (String arg : args) {
            switch (arg) {
                case "--tokens":  showTokens = true;  break;
                case "--ast":     showAst = true;     break;
                case "--graph":   showGraph = true;    break;
                case "--ast-gui": showAstGui = true;  break;
                case "--gui":     showGui = true;     break;
                default:          filePath = arg;     break;
            }
        }

        if (filePath == null) {
            System.err.println("Error: no input file specified.");
            System.exit(1);
        }

        

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            System.err.println("Error: file not found – " + filePath);
            System.exit(1);
        }

        try {
            CharStream input = CharStreams.fromPath(path);

            ELSDLexer lexer = new ELSDLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            tokens.fill();

            if (showTokens) {
                printTokens(tokens, lexer);
            }

            ELSDParser parser = new ELSDParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(new DiagnosticErrorListener());

            ELSDParser.ProgramContext tree = parser.program();

            int errorCount = parser.getNumberOfSyntaxErrors();
            System.out.println();

            System.out.println("  ELSD Parse Result");
            System.out.println("  File   : " + filePath);
            System.out.println("  Tokens : " + tokens.getTokens().size());
            System.out.println("  Errors : " + errorCount);
            System.out.println();

            if (errorCount == 0) {
                System.out.println("Parse successful – no syntax errors.");
            } else {
                System.out.println("Parse completed with " + errorCount + " syntax error(s).");
            }

            System.out.println();
            System.out.println("── Parse Tree (LISP)");
            String lispTree = tree.toStringTree(parser);
            System.out.println(prettyPrintTree(lispTree));

            if (showAst) {
                System.out.println();
                System.out.println("── Abstract Syntax Tree");
                ASTBuilder builder = new ASTBuilder();
                ASTNode.Program ast = (ASTNode.Program) builder.visit(tree);
                ASTPrinter printer = new ASTPrinter();
                System.out.println(printer.print(ast));
            }

            if (showGraph) {
                ASTBuilder builder = new ASTBuilder();
                ASTNode.Program ast = (ASTNode.Program) builder.visit(tree);
                ASTDotExporter exporter = new ASTDotExporter();
                String dot = exporter.export(ast);
                String dotFile = "ast.dot";
                try (FileWriter fw = new FileWriter(dotFile)) {
                    fw.write(dot);
                }
                System.out.println("DOT file saved to: " + dotFile);
                System.out.println("Rendering graph...");
                ProcessBuilder pb = new ProcessBuilder("python3", "render_ast.py", dotFile);
                pb.inheritIO();
                Process p = pb.start();
                p.waitFor();
            }

            if (showAstGui) {
                System.out.println("Opening AST graphical viewer...");
                ASTBuilder builder = new ASTBuilder();
                ASTNode.Program ast = (ASTNode.Program) builder.visit(tree);
                ASTTreeViewer.show(ast);
            }

            if (showGui) {
                System.out.println();
                System.out.println("Opening parse-tree GUI...");
                JFrame frame = new JFrame("ELSD Parse Tree");
                TreeViewer viewer = new TreeViewer(
                        Arrays.asList(parser.getRuleNames()), tree);
                viewer.setScale(1.2);
                JScrollPane scrollPane = new JScrollPane(viewer);
                frame.add(scrollPane);
                frame.setSize(1200, 800);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printTokens(CommonTokenStream tokens, ELSDLexer lexer) {
        System.out.println();
        System.out.println("── Token Stream");
        System.out.printf("%-6s %-20s %-25s %s%n", "INDEX", "TOKEN TYPE", "TEXT", "LINE:COL");
        System.out.println("─".repeat(80));

        for (Token tok : tokens.getTokens()) {
            if (tok.getType() == Token.EOF) {
                System.out.printf("%-6d %-20s %-25s %d:%d%n",
                        tok.getTokenIndex(), "EOF", "<EOF>",
                        tok.getLine(), tok.getCharPositionInLine());
                break;
            }
            String typeName = lexer.getVocabulary().getSymbolicName(tok.getType());
            if (typeName == null) typeName = String.valueOf(tok.getType());

            String text = tok.getText().replace("\n", "\\n").replace("\r", "\\r");
            if (text.length() > 24) text = text.substring(0, 21) + "...";

            System.out.printf("%-6d %-20s %-25s %d:%d%n",
                    tok.getTokenIndex(), typeName, "'" + text + "'",
                    tok.getLine(), tok.getCharPositionInLine());
        }
        System.out.println();
    }

    // reflows the flat LISP string into indented multi-line output
    private static String prettyPrintTree(String lispTree) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean afterSpace = false;

        for (int i = 0; i < lispTree.length(); i++) {
            char c = lispTree.charAt(i);
            switch (c) {
                case '(':
                    indent++;
                    sb.append('\n');
                    sb.append("  ".repeat(indent));
                    sb.append(c);
                    afterSpace = false;
                    break;
                case ')':
                    indent--;
                    sb.append(c);
                    afterSpace = false;
                    break;
                case ' ':
                    if (!afterSpace) {
                        sb.append(' ');
                    }
                    afterSpace = true;
                    break;
                default:
                    sb.append(c);
                    afterSpace = false;
                    break;
            }
        }
        return sb.toString();
    }

    private static class DiagnosticErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol,
                                int line, int charPositionInLine,
                                String msg,
                                RecognitionException e) {
            System.err.printf("  ERROR  line %d:%d  –  %s%n", line, charPositionInLine, msg);
        }
    }
}
