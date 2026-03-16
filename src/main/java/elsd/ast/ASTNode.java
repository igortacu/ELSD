package elsd.ast;

import java.util.List;
import java.util.ArrayList;

public abstract class ASTNode {

    public int line;
    public int col;

    public abstract <T> T accept(ASTVisitor<T> visitor);

    // --- top level ---

    public static class Program extends ASTNode {
        public final List<ASTNode> statements = new ArrayList<>();
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitProgram(this); }
    }

    // --- declarations ---

    // gene myGene = value
    public static class Declaration extends ASTNode {
        public String type;
        public List<String> ids;
        public Expression initValue;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitDeclaration(this); }
    }

    // --- assignments ---

    // field id = expr
    public static class AssignExpr extends ASTNode {
        public String field;
        public String id;
        public Expression value;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitAssignExpr(this); }
    }

    // field id1 id2 = expr1 expr2
    public static class AssignMulti extends ASTNode {
        public String field;
        public List<String> ids;
        public List<Expression> values;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitAssignMulti(this); }
    }

    // A dominant over a
    public static class AssignDominance extends ASTNode {
        public String dominant;
        public String recessive;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitAssignDominance(this); }
    }

    // --- expressions ---

    public static abstract class Expression extends ASTNode {}

    public static class NumberLiteral extends Expression {
        public String value;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitNumberLiteral(this); }
    }

    public static class StringLiteral extends Expression {
        public String value;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitStringLiteral(this); }
    }

    public static class BooleanLiteral extends Expression {
        public boolean value;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitBooleanLiteral(this); }
    }

    public static class Identifier extends Expression {
        public String name;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitIdentifier(this); }
    }

    public static class UnaryExpr extends Expression {
        public String op;
        public Expression operand;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitUnaryExpr(this); }
    }

    public static class BinaryExpr extends Expression {
        public String op;
        public Expression left;
        public Expression right;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitBinaryExpr(this); }
    }

    public static class EventExpr extends Expression {
        public Event event;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitEventExpr(this); }
    }

    // --- conditions ---

    public static abstract class Condition extends ASTNode {}

    public static class CompareCondition extends Condition {
        public Expression left;
        public String op;
        public Expression right;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitCompareCondition(this); }
    }

    public static class LogicalCondition extends Condition {
        public String op;
        public Condition left;
        public Condition right;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitLogicalCondition(this); }
    }

    public static class NotCondition extends Condition {
        public Condition operand;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitNotCondition(this); }
    }

    // --- flow structures ---

    // if / elif / else chain; branches[0] is always the "if"
    public static class IfStatement extends ASTNode {
        public final List<ConditionBlock> branches = new ArrayList<>();
        public List<ASTNode> elseBody;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitIfStatement(this); }
    }

    public static class ConditionBlock {
        public Condition condition;
        public List<ASTNode> body;
    }

    public static class TernaryStatement extends ASTNode {
        public Condition condition;
        public ASTNode thenBranch;
        public ASTNode elseBranch;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitTernaryStatement(this); }
    }

    public static class WhileStatement extends ASTNode {
        public Condition condition;
        public List<ASTNode> body;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitWhileStatement(this); }
    }

    public static class ForStatement extends ASTNode {
        public String variable;
        public List<Expression> iterable;
        public List<ASTNode> body;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitForStatement(this); }
    }

    // --- computations ---

    // looks up a named field on a gene, optionally scoped to a specific generation
    public static class FindExpr extends ASTNode {
        public String field;
        public String id;
        public Integer generation;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitFindExpr(this); }
    }

    // mendelian cross of two parents producing named offspring; ratios are optional
    public static class CrossExpr extends ASTNode {
        public String parent1;
        public String parent2;
        public String offspring;
        public List<Expression> ratios;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitCrossExpr(this); }
    }

    // predicts trait distribution across a generation
    public static class PredExpr extends ASTNode {
        public List<String> ids;
        public Integer generation;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitPredExpr(this); }
    }

    // assigns a quantitative value to a trait with optional confidence interval
    public static class EstimateExpr extends ASTNode {
        public String id;
        public String value;
        public String confidence;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitEstimateExpr(this); }
    }

    // infers either parent genotypes or a specific field from observed data
    public static class InferExpr extends ASTNode {
        public boolean inferParents;
        public String field;
        public String sourceId;
        public List<String> additionalIds;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitInferExpr(this); }
    }

    // probability of events; supports conditional form P(A | B, C)
    public static class ProbExpr extends ASTNode {
        public List<Event> events;
        public List<Event> givenEvents;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitProbExpr(this); }
    }

    // genetic linkage — tracks recombination frequency between loci
    public static class LinkExpr extends ASTNode {
        public List<String> ids;
        public String recombination;
        public String distance;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitLinkExpr(this); }
    }

    // sex-linked trait, optionally with a field assignment
    public static class SexExpr extends ASTNode {
        public String id;
        public String field;
        public Expression value;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitSexExpr(this); }
    }

    // ABO / Rh blood group analysis for one or more individuals
    public static class BloodExpr extends ASTNode {
        public List<String> ids;
        public String system;
        public List<Expression> phenotypes;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitBloodExpr(this); }
    }

    // --- events ---

    // phenotype(X), genotype(X), or carries(X, alleles)
    public static class Event extends ASTNode {
        public String kind;
        public String id;
        public List<String> alleles;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitEvent(this); }
    }

    // --- I/O ---

    public static class PrintStatement extends ASTNode {
        public String field;
        public String targetId;
        public boolean printAll;
        public List<Expression> expressions;
        public List<Event> events;
        @Override public <T> T accept(ASTVisitor<T> v) { return v.visitPrintStatement(this); }
    }
}
