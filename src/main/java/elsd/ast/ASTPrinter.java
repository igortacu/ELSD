package elsd.ast;

import elsd.ast.ASTNode.*;

import java.util.List;

// renders the AST as an indented text tree; delegates all formatting through the visitor
public class ASTPrinter implements ASTVisitor<String> {

    private int indent = 0;

    public String print(ASTNode node) {
        return node.accept(this);
    }

    private String pad() {
        return "  ".repeat(indent);
    }

    private String line(String text) {
        return pad() + text + "\n";
    }

    private String visitChildren(List<? extends ASTNode> nodes) {
        StringBuilder sb = new StringBuilder();
        indent++;
        for (ASTNode n : nodes) {
            sb.append(n.accept(this));
        }
        indent--;
        return sb.toString();
    }

    @Override
    public String visitProgram(Program node) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("Program"));
        sb.append(visitChildren(node.statements));
        return sb.toString();
    }

    @Override
    public String visitDeclaration(Declaration node) {
        StringBuilder sb = new StringBuilder();
        String init = node.initValue != null ? " = " + node.initValue.accept(this).trim() : "";
        sb.append(line("Declaration [type=" + node.type + ", ids=" + node.ids + init + "]"));
        return sb.toString();
    }

    @Override
    public String visitAssignExpr(AssignExpr node) {
        StringBuilder sb = new StringBuilder();
        String f = node.field != null ? " field=" + node.field : "";
        sb.append(line("Assign [id=" + node.id + f + "]"));
        if (node.value != null) {
            indent++;
            sb.append(line("= " + node.value.accept(this).trim()));
            indent--;
        }
        return sb.toString();
    }

    @Override
    public String visitAssignMulti(AssignMulti node) {
        StringBuilder sb = new StringBuilder();
        String f = node.field != null ? " field=" + node.field : "";
        sb.append(line("AssignMulti [ids=" + node.ids + f + "]"));
        indent++;
        for (Expression v : node.values) {
            sb.append(line("= " + v.accept(this).trim()));
        }
        indent--;
        return sb.toString();
    }

    @Override
    public String visitAssignDominance(AssignDominance node) {
        return line("Dominance [" + node.dominant + " -> " + node.recessive + "]");
    }

    @Override
    public String visitNumberLiteral(NumberLiteral node) {
        return line(node.value);
    }

    @Override
    public String visitStringLiteral(StringLiteral node) {
        return line("\"" + node.value + "\"");
    }

    @Override
    public String visitBooleanLiteral(BooleanLiteral node) {
        return line(String.valueOf(node.value));
    }

    @Override
    public String visitIdentifier(Identifier node) {
        return line(node.name);
    }

    @Override
    public String visitUnaryExpr(UnaryExpr node) {
        return line("(" + node.op + " " + node.operand.accept(this).trim() + ")");
    }

    @Override
    public String visitBinaryExpr(BinaryExpr node) {
        return line("(" + node.left.accept(this).trim()
                + " " + node.op + " "
                + node.right.accept(this).trim() + ")");
    }

    @Override
    public String visitEventExpr(EventExpr node) {
        return node.event.accept(this);
    }

    @Override
    public String visitCompareCondition(CompareCondition node) {
        return line("(" + node.left.accept(this).trim()
                + " " + node.op + " "
                + node.right.accept(this).trim() + ")");
    }

    @Override
    public String visitLogicalCondition(LogicalCondition node) {
        return line("(" + node.left.accept(this).trim()
                + " " + node.op + " "
                + node.right.accept(this).trim() + ")");
    }

    @Override
    public String visitNotCondition(NotCondition node) {
        return line("(not " + node.operand.accept(this).trim() + ")");
    }

    @Override
    public String visitIfStatement(IfStatement node) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < node.branches.size(); i++) {
            ConditionBlock b = node.branches.get(i);
            String kw = (i == 0) ? "If" : "Elif";
            sb.append(line(kw + " " + b.condition.accept(this).trim()));
            sb.append(visitChildren(b.body));
        }
        if (node.elseBody != null) {
            sb.append(line("Else"));
            sb.append(visitChildren(node.elseBody));
        }
        return sb.toString();
    }

    @Override
    public String visitTernaryStatement(TernaryStatement node) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("Ternary " + node.condition.accept(this).trim()));
        indent++;
        sb.append(line("then:"));
        indent++;
        sb.append(node.thenBranch.accept(this));
        indent--;
        sb.append(line("else:"));
        indent++;
        sb.append(node.elseBranch.accept(this));
        indent--;
        indent--;
        return sb.toString();
    }

    @Override
    public String visitWhileStatement(WhileStatement node) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("While " + node.condition.accept(this).trim()));
        sb.append(visitChildren(node.body));
        return sb.toString();
    }

    @Override
    public String visitForStatement(ForStatement node) {
        StringBuilder sb = new StringBuilder();
        List<String> vals = node.iterable.stream()
                .map(e -> e.accept(this).trim())
                .collect(java.util.stream.Collectors.toList());
        sb.append(line("For " + node.variable + " in [" + String.join(", ", vals) + "]"));
        sb.append(visitChildren(node.body));
        return sb.toString();
    }

    @Override
    public String visitFindExpr(FindExpr node) {
        String gen = node.generation != null ? " gen=" + node.generation : "";
        return line("Find [field=" + node.field + ", id=" + node.id + gen + "]");
    }

    @Override
    public String visitCrossExpr(CrossExpr node) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("Cross [" + node.parent1 + " x " + node.parent2 + " -> " + node.offspring + "]"));
        if (node.ratios != null) {
            indent++;
            List<String> rs = node.ratios.stream()
                    .map(e -> e.accept(this).trim())
                    .collect(java.util.stream.Collectors.toList());
            sb.append(line("ratio: " + String.join(", ", rs)));
            indent--;
        }
        return sb.toString();
    }

    @Override
    public String visitPredExpr(PredExpr node) {
        String gen = node.generation != null ? " gen=" + node.generation : "";
        return line("Pred [ids=" + node.ids + gen + "]");
    }

    @Override
    public String visitEstimateExpr(EstimateExpr node) {
        String conf = node.confidence != null ? " confidence=" + node.confidence : "";
        return line("Estimate [id=" + node.id + ", value=" + node.value + conf + "]");
    }

    @Override
    public String visitInferExpr(InferExpr node) {
        if (node.inferParents) {
            return line("Infer parents from " + node.sourceId
                    + (node.additionalIds.isEmpty() ? "" : ", " + node.additionalIds));
        }
        return line("Infer [field=" + node.field + "] from " + node.sourceId
                + (node.additionalIds.isEmpty() ? "" : ", " + node.additionalIds));
    }

    @Override
    public String visitProbExpr(ProbExpr node) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("Probability"));
        indent++;
        sb.append(line("events: " + eventListStr(node.events)));
        if (node.givenEvents != null) {
            sb.append(line("given:  " + eventListStr(node.givenEvents)));
        }
        indent--;
        return sb.toString();
    }

    @Override
    public String visitLinkExpr(LinkExpr node) {
        String dist = node.distance != null ? " distance=" + node.distance : "";
        return line("Linkage [ids=" + node.ids + ", recomb=" + node.recombination + dist + "]");
    }

    @Override
    public String visitSexExpr(SexExpr node) {
        StringBuilder sb = new StringBuilder();
        sb.append("SexLinked [id=" + node.id);
        if (node.field != null) sb.append(", field=" + node.field);
        if (node.value != null) sb.append(", value=" + node.value.accept(this).trim());
        sb.append("]");
        return line(sb.toString());
    }

    @Override
    public String visitBloodExpr(BloodExpr node) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("BloodGroup [ids=" + node.ids + ", system=" + node.system + "]"));
        if (node.phenotypes != null) {
            indent++;
            List<String> ps = node.phenotypes.stream()
                    .map(e -> e.accept(this).trim())
                    .collect(java.util.stream.Collectors.toList());
            sb.append(line("phenotypes: " + String.join(", ", ps)));
            indent--;
        }
        return sb.toString();
    }

    @Override
    public String visitEvent(Event node) {
        if ("carries".equals(node.kind)) {
            return line("Event [" + node.kind + "(" + node.id + ", " + node.alleles + ")]");
        }
        return line("Event [" + node.kind + "(" + node.id + ")]");
    }

    private String eventListStr(List<Event> events) {
        return events.stream()
                .map(e -> {
                    if ("carries".equals(e.kind))
                        return e.kind + "(" + e.id + ", " + e.alleles + ")";
                    return e.kind + "(" + e.id + ")";
                })
                .collect(java.util.stream.Collectors.joining(", "));
    }

    @Override
    public String visitPrintStatement(PrintStatement node) {
        StringBuilder sb = new StringBuilder();
        sb.append("Print");
        if (node.targetId != null) {
            sb.append(" " + node.targetId);
        }
        if (node.field != null) {
            sb.append(" [field=" + node.field + "]");
        }
        if (node.printAll) {
            sb.append(" ALL");
        }
        if (node.expressions != null) {
            List<String> es = node.expressions.stream()
                    .map(e -> e.accept(this).trim())
                    .collect(java.util.stream.Collectors.toList());
            sb.append(" " + String.join(", ", es));
        }
        if (node.events != null) {
            sb.append(" " + eventListStr(node.events));
        }
        return line(sb.toString());
    }
}
