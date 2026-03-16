package elsd.ast;

import elsd.ast.ASTNode.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Dimension;
import java.awt.Font;

public class ASTTreeViewer {

    public static void show(ASTNode root) {
        DefaultMutableTreeNode treeRoot = buildSwingTree(root);

        JTree jTree = new JTree(treeRoot);
        jTree.setFont(new Font("Monospaced", Font.PLAIN, 14));
        jTree.setRowHeight(22);

        // pre-expand all rows; getRowCount() grows as rows expand, so iterate forward
        for (int i = 0; i < jTree.getRowCount(); i++) {
            jTree.expandRow(i);
        }

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setLeafIcon(null);
        renderer.setOpenIcon(null);
        renderer.setClosedIcon(null);
        jTree.setCellRenderer(renderer);

        JScrollPane scrollPane = new JScrollPane(jTree);
        scrollPane.setPreferredSize(new Dimension(900, 700));

        JFrame frame = new JFrame("ELSD - Abstract Syntax Tree");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.add(scrollPane);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static DefaultMutableTreeNode buildSwingTree(ASTNode node) {
        if (node == null) return new DefaultMutableTreeNode("null");

        if (node instanceof Program) {
            Program p = (Program) node;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("Program");
            for (ASTNode stmt : p.statements) {
                n.add(buildSwingTree(stmt));
            }
            return n;
        }

        if (node instanceof Declaration) {
            Declaration d = (Declaration) node;
            String label = "Declaration [" + d.type + "] " + d.ids;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode(label);
            if (d.initValue != null) {
                n.add(buildSwingTree(d.initValue));
            }
            return n;
        }

        if (node instanceof AssignExpr) {
            AssignExpr a = (AssignExpr) node;
            String f = a.field != null ? a.field + " " : "";
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("Assign " + f + a.id);
            if (a.value != null) {
                n.add(buildSwingTree(a.value));
            }
            return n;
        }

        if (node instanceof AssignMulti) {
            AssignMulti a = (AssignMulti) node;
            String f = a.field != null ? a.field + " " : "";
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("AssignMulti " + f + a.ids);
            for (Expression v : a.values) {
                n.add(buildSwingTree(v));
            }
            return n;
        }

        if (node instanceof AssignDominance) {
            AssignDominance a = (AssignDominance) node;
            return new DefaultMutableTreeNode("Dominance: " + a.dominant + " > " + a.recessive);
        }

        if (node instanceof NumberLiteral) {
            return new DefaultMutableTreeNode("Number: " + ((NumberLiteral) node).value);
        }

        if (node instanceof StringLiteral) {
            return new DefaultMutableTreeNode("String: \"" + ((StringLiteral) node).value + "\"");
        }

        if (node instanceof BooleanLiteral) {
            return new DefaultMutableTreeNode("Boolean: " + ((BooleanLiteral) node).value);
        }

        if (node instanceof Identifier) {
            return new DefaultMutableTreeNode("Id: " + ((Identifier) node).name);
        }

        if (node instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) node;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("Unary: " + u.op);
            n.add(buildSwingTree(u.operand));
            return n;
        }

        if (node instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) node;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("BinaryOp: " + b.op);
            n.add(buildSwingTree(b.left));
            n.add(buildSwingTree(b.right));
            return n;
        }

        if (node instanceof EventExpr) {
            EventExpr ee = (EventExpr) node;
            return buildSwingTree(ee.event);
        }

        if (node instanceof CompareCondition) {
            CompareCondition c = (CompareCondition) node;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("Compare: " + c.op);
            n.add(buildSwingTree(c.left));
            n.add(buildSwingTree(c.right));
            return n;
        }

        if (node instanceof LogicalCondition) {
            LogicalCondition c = (LogicalCondition) node;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("Logical: " + c.op);
            n.add(buildSwingTree(c.left));
            n.add(buildSwingTree(c.right));
            return n;
        }

        if (node instanceof NotCondition) {
            NotCondition c = (NotCondition) node;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("Not");
            n.add(buildSwingTree(c.operand));
            return n;
        }

        if (node instanceof IfStatement) {
            IfStatement s = (IfStatement) node;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("If");
            for (int i = 0; i < s.branches.size(); i++) {
                ConditionBlock b = s.branches.get(i);
                String kw = (i == 0) ? "if" : "elif";
                DefaultMutableTreeNode branch = new DefaultMutableTreeNode(kw);
                branch.add(labeled("condition", buildSwingTree(b.condition)));
                DefaultMutableTreeNode body = new DefaultMutableTreeNode("body");
                for (ASTNode stmt : b.body) {
                    body.add(buildSwingTree(stmt));
                }
                branch.add(body);
                n.add(branch);
            }
            if (s.elseBody != null) {
                DefaultMutableTreeNode elseBranch = new DefaultMutableTreeNode("else");
                for (ASTNode stmt : s.elseBody) {
                    elseBranch.add(buildSwingTree(stmt));
                }
                n.add(elseBranch);
            }
            return n;
        }

        if (node instanceof TernaryStatement) {
            TernaryStatement t = (TernaryStatement) node;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("Ternary");
            n.add(labeled("condition", buildSwingTree(t.condition)));
            n.add(labeled("then", buildSwingTree(t.thenBranch)));
            n.add(labeled("else", buildSwingTree(t.elseBranch)));
            return n;
        }

        if (node instanceof WhileStatement) {
            WhileStatement w = (WhileStatement) node;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("While");
            n.add(labeled("condition", buildSwingTree(w.condition)));
            DefaultMutableTreeNode body = new DefaultMutableTreeNode("body");
            for (ASTNode stmt : w.body) {
                body.add(buildSwingTree(stmt));
            }
            n.add(body);
            return n;
        }

        if (node instanceof ForStatement) {
            ForStatement f = (ForStatement) node;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("For: " + f.variable);
            DefaultMutableTreeNode iter = new DefaultMutableTreeNode("iterable");
            for (Expression e : f.iterable) {
                iter.add(buildSwingTree(e));
            }
            n.add(iter);
            DefaultMutableTreeNode body = new DefaultMutableTreeNode("body");
            for (ASTNode stmt : f.body) {
                body.add(buildSwingTree(stmt));
            }
            n.add(body);
            return n;
        }

        if (node instanceof FindExpr) {
            FindExpr f = (FindExpr) node;
            String gen = f.generation != null ? " gen=" + f.generation : "";
            return new DefaultMutableTreeNode("Find: " + f.field + " " + f.id + gen);
        }

        if (node instanceof CrossExpr) {
            CrossExpr c = (CrossExpr) node;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode(
                    "Cross: " + c.parent1 + " x " + c.parent2 + " -> " + c.offspring);
            if (c.ratios != null) {
                DefaultMutableTreeNode ratios = new DefaultMutableTreeNode("ratios");
                for (Expression r : c.ratios) {
                    ratios.add(buildSwingTree(r));
                }
                n.add(ratios);
            }
            return n;
        }

        if (node instanceof PredExpr) {
            PredExpr p = (PredExpr) node;
            String gen = p.generation != null ? " gen=" + p.generation : "";
            return new DefaultMutableTreeNode("Predict: " + p.ids + gen);
        }

        if (node instanceof EstimateExpr) {
            EstimateExpr e = (EstimateExpr) node;
            String conf = e.confidence != null ? " conf=" + e.confidence : "";
            return new DefaultMutableTreeNode("Estimate: " + e.id + " val=" + e.value + conf);
        }

        if (node instanceof InferExpr) {
            InferExpr i = (InferExpr) node;
            if (i.inferParents) {
                return new DefaultMutableTreeNode("Infer parents from " + i.sourceId);
            }
            return new DefaultMutableTreeNode("Infer " + i.field + " from " + i.sourceId);
        }

        if (node instanceof ProbExpr) {
            ProbExpr p = (ProbExpr) node;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("Probability");
            DefaultMutableTreeNode events = new DefaultMutableTreeNode("events");
            for (Event ev : p.events) {
                events.add(buildSwingTree(ev));
            }
            n.add(events);
            if (p.givenEvents != null) {
                DefaultMutableTreeNode given = new DefaultMutableTreeNode("given");
                for (Event ev : p.givenEvents) {
                    given.add(buildSwingTree(ev));
                }
                n.add(given);
            }
            return n;
        }

        if (node instanceof LinkExpr) {
            LinkExpr l = (LinkExpr) node;
            String dist = l.distance != null ? " dist=" + l.distance : "";
            return new DefaultMutableTreeNode("Linkage: " + l.ids + " recomb=" + l.recombination + dist);
        }

        if (node instanceof SexExpr) {
            SexExpr s = (SexExpr) node;
            String extra = "";
            if (s.field != null) extra += " " + s.field;
            return new DefaultMutableTreeNode("SexLinked: " + s.id + extra);
        }

        if (node instanceof BloodExpr) {
            BloodExpr b = (BloodExpr) node;
            DefaultMutableTreeNode n = new DefaultMutableTreeNode("BloodGroup: " + b.ids + " " + b.system);
            if (b.phenotypes != null) {
                DefaultMutableTreeNode ph = new DefaultMutableTreeNode("phenotypes");
                for (Expression e : b.phenotypes) {
                    ph.add(buildSwingTree(e));
                }
                n.add(ph);
            }
            return n;
        }

        if (node instanceof Event) {
            Event ev = (Event) node;
            String label = "Event: " + ev.kind + "(" + ev.id;
            if (ev.alleles != null) label += ", " + ev.alleles;
            label += ")";
            return new DefaultMutableTreeNode(label);
        }

        if (node instanceof PrintStatement) {
            PrintStatement p = (PrintStatement) node;
            StringBuilder label = new StringBuilder("Print");
            if (p.targetId != null) label.append(" ").append(p.targetId);
            if (p.field != null) label.append(" [").append(p.field).append("]");
            if (p.printAll) label.append(" ALL");
            DefaultMutableTreeNode n = new DefaultMutableTreeNode(label.toString());
            if (p.expressions != null) {
                for (Expression e : p.expressions) {
                    n.add(buildSwingTree(e));
                }
            }
            if (p.events != null) {
                for (Event ev : p.events) {
                    n.add(buildSwingTree(ev));
                }
            }
            return n;
        }

        return new DefaultMutableTreeNode(node.getClass().getSimpleName());
    }

    private static DefaultMutableTreeNode labeled(String label, DefaultMutableTreeNode child) {
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(label);
        n.add(child);
        return n;
    }
}
