package elsd;

import elsd.ast.ASTNode.*;
import elsd.ast.ASTNode;
import elsd.ast.ASTVisitor;

import java.util.*;

/**
 * Full ELSD interpreter — evaluates every DSL construct:
 * declarations, assignments, dominance, control flow, cross/Punnett,
 * probability, linkage, sex-linked, blood groups, pred, estimate, infer, print.
 */
public class Interpreter implements ASTVisitor<Object> {

    // ── Core state ────────────────────────────────────────────────────────────

    /** Declared identifiers by type: "gene" → {"eyeColor", "A"} */
    private final Map<String, Set<String>> declared = new LinkedHashMap<>();

    /** Field values: fieldName → (id → value).  E.g. "genotype" → {"A" → "Aa"} */
    private final Map<String, Map<String, Object>> fields = new LinkedHashMap<>();

    // ── Genetics state ────────────────────────────────────────────────────────

    /** Dominant allele → recessive allele.  E.g. "A" → "a" */
    private final Map<String, String> dominance = new LinkedHashMap<>();

    /** Offspring id → (genotype string → frequency).  Populated by cross. */
    private final Map<String, Map<String, Double>> crossResults = new LinkedHashMap<>();

    /** Sorted "geneA,geneB" → recombination rate (0–0.5) */
    private final Map<String, Double> linkageMap = new LinkedHashMap<>();

    /** Gene ids that are X-linked (sex-linked) */
    private final Set<String> sexLinked = new LinkedHashSet<>();

    // ═════════════════════════════════════════════════════════════════════════
    // Top-level
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public Object visitProgram(Program node) {
        for (ASTNode stmt : node.statements) {
            if (stmt != null) stmt.accept(this);
        }
        return null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Declarations & Assignments
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public Object visitDeclaration(Declaration node) {
        Set<String> s = declared.computeIfAbsent(node.type, k -> new LinkedHashSet<>());
        s.addAll(node.ids);
        if (node.initValue != null && node.ids.size() == 1) {
            Object val = node.initValue.accept(this);
            fields.computeIfAbsent(node.type, k -> new LinkedHashMap<>())
                  .put(node.ids.get(0), val);
        }
        return null;
    }

    @Override
    public Object visitAssignExpr(AssignExpr node) {
        Object val = node.value != null ? node.value.accept(this) : null;
        String f = node.field != null ? node.field : "value";
        fields.computeIfAbsent(f, k -> new LinkedHashMap<>()).put(node.id, val);
        return null;
    }

    @Override
    public Object visitAssignMulti(AssignMulti node) {
        if (node.ids != null && node.values != null) {
            for (int i = 0; i < node.ids.size() && i < node.values.size(); i++) {
                Object val = node.values.get(i).accept(this);
                fields.computeIfAbsent(node.field, k -> new LinkedHashMap<>())
                      .put(node.ids.get(i), val);
            }
        }
        return null;
    }

    @Override
    public Object visitAssignDominance(AssignDominance node) {
        dominance.put(node.dominant, node.recessive);
        return null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Expressions
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public Object visitNumberLiteral(NumberLiteral node) {
        try {
            if (node.value.contains(".")) return Double.parseDouble(node.value);
            return Integer.parseInt(node.value);
        } catch (Exception e) {
            return node.value;
        }
    }

    @Override
    public Object visitStringLiteral(StringLiteral node) {
        return node.value;
    }

    @Override
    public Object visitBooleanLiteral(BooleanLiteral node) {
        return node.value;
    }

    /**
     * Looks up an identifier's value from the fields maps.
     * Priority: "value" (direct assign) → "phenotype" → "genotype" → any other field.
     * Falls back to the raw name so gene/parent ids used as string refs still work.
     */
    @Override
    public Object visitIdentifier(Identifier node) {
        String[] priority = {"value", "phenotype", "genotype"};
        for (String f : priority) {
            Map<String, Object> m = fields.get(f);
            if (m != null && m.containsKey(node.name)) return m.get(node.name);
        }
        for (Map.Entry<String, Map<String, Object>> e : fields.entrySet()) {
            if (e.getValue().containsKey(node.name)) return e.getValue().get(node.name);
        }
        return node.name;
    }

    @Override
    public Object visitUnaryExpr(UnaryExpr node) {
        Object v = node.operand.accept(this);
        if ("-".equals(node.op) && v instanceof Number) {
            if (v instanceof Integer) return -((Integer) v);
            if (v instanceof Double)  return -((Double) v);
        }
        return v;
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr node) {
        Object l = node.left.accept(this);
        Object r = node.right.accept(this);
        if (l instanceof Number && r instanceof Number) {
            double ld = ((Number) l).doubleValue();
            double rd = ((Number) r).doubleValue();
            switch (node.op) {
                case "+": return ld + rd;
                case "-": return ld - rd;
                case "*": return ld * rd;
                case "/": return rd == 0 ? Double.NaN : ld / rd;
            }
        }
        // String concatenation for +
        if ("+".equals(node.op)) return String.valueOf(l) + String.valueOf(r);
        return null;
    }

    @Override
    public Object visitEventExpr(EventExpr node) {
        return node.event;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Conditions
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public Object visitCompareCondition(CompareCondition node) {
        Object l = node.left.accept(this);
        Object r = node.right.accept(this);
        return compareObjects(l, node.op, r);
    }

    @Override
    public Object visitLogicalCondition(LogicalCondition node) {
        boolean l = isTruthy(node.left.accept(this));
        if ("and".equals(node.op)) return l && isTruthy(node.right.accept(this));
        return l || isTruthy(node.right.accept(this));
    }

    @Override
    public Object visitNotCondition(NotCondition node) {
        return !isTruthy(node.operand.accept(this));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Flow control
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public Object visitIfStatement(IfStatement node) {
        for (ConditionBlock branch : node.branches) {
            if (isTruthy(branch.condition.accept(this))) {
                for (ASTNode stmt : branch.body) stmt.accept(this);
                return null;
            }
        }
        if (node.elseBody != null) {
            for (ASTNode stmt : node.elseBody) stmt.accept(this);
        }
        return null;
    }

    @Override
    public Object visitTernaryStatement(TernaryStatement node) {
        if (isTruthy(node.condition.accept(this))) {
            node.thenBranch.accept(this);
        } else if (node.elseBranch != null) {
            node.elseBranch.accept(this);
        }
        return null;
    }

    @Override
    public Object visitWhileStatement(WhileStatement node) {
        int guard = 10_000;
        while (guard-- > 0 && isTruthy(node.condition.accept(this))) {
            for (ASTNode stmt : node.body) stmt.accept(this);
        }
        return null;
    }

    @Override
    public Object visitForStatement(ForStatement node) {
        for (Expression item : node.iterable) {
            Object val = item.accept(this);
            fields.computeIfAbsent("value", k -> new LinkedHashMap<>())
                  .put(node.variable, val);
            for (ASTNode stmt : node.body) stmt.accept(this);
        }
        return null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Genetics helpers
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Parses a genotype string into ordered allele pairs.
     * "AaBb" → [["A","a"], ["B","b"]]
     * "AA"   → [["A","A"]]
     * Walks two characters at a time; a pair is two chars that are case-variants
     * of the same letter (e.g. 'A' and 'a').
     */
    private List<String[]> parseGenotype(String geno) {
        List<String[]> pairs = new ArrayList<>();
        if (geno == null || geno.isEmpty()) return pairs;
        int i = 0;
        while (i + 1 < geno.length()) {
            char c1 = geno.charAt(i), c2 = geno.charAt(i + 1);
            if (Character.toLowerCase(c1) == Character.toLowerCase(c2)) {
                pairs.add(new String[]{String.valueOf(c1), String.valueOf(c2)});
                i += 2;
            } else {
                i++;
            }
        }
        return pairs;
    }

    /**
     * Generates all gametes with independent assortment.
     * "AaBb" → {AB:0.25, Ab:0.25, aB:0.25, ab:0.25}
     */
    private Map<String, Double> generateGametes(List<String[]> pairs) {
        Map<String, Double> gametes = new LinkedHashMap<>();
        gametes.put("", 1.0);
        for (String[] pair : pairs) {
            Map<String, Double> next = new LinkedHashMap<>();
            for (Map.Entry<String, Double> e : gametes.entrySet()) {
                next.merge(e.getKey() + pair[0], e.getValue() * 0.5, Double::sum);
                next.merge(e.getKey() + pair[1], e.getValue() * 0.5, Double::sum);
            }
            gametes = next;
        }
        return gametes;
    }

    /**
     * Combines two gamete strings into a canonical genotype: uppercase allele first.
     * "AB" + "ab" → "AaBb"
     */
    private String combineGametes(String g1, String g2) {
        StringBuilder sb = new StringBuilder();
        int len = Math.min(g1.length(), g2.length());
        for (int i = 0; i < len; i++) {
            char a = g1.charAt(i), b = g2.charAt(i);
            if (Character.isUpperCase(b) && Character.isLowerCase(a)) {
                sb.append(b).append(a);
            } else {
                sb.append(a).append(b);
            }
        }
        return sb.toString();
    }

    /**
     * Punnett square: cross all gametes from g1 × g2, merge duplicate genotypes.
     */
    private Map<String, Double> punnettSquare(Map<String, Double> g1, Map<String, Double> g2) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e1 : g1.entrySet()) {
            for (Map.Entry<String, Double> e2 : g2.entrySet()) {
                String geno = combineGametes(e1.getKey(), e2.getKey());
                result.merge(geno, e1.getValue() * e2.getValue(), Double::sum);
            }
        }
        return result;
    }

    /**
     * Maps a genotype string to its phenotype label using the dominance map.
     * Works locus-by-locus:
     *   - If the uppercase allele is registered as dominant → show uppercase
     *   - If no dominance info → show both alleles (codominant)
     *   - If both alleles are lowercase (homozygous recessive) → show lowercase
     */
    private String genotypeToPhenotype(String geno) {
        List<String[]> pairs = parseGenotype(geno);
        if (pairs.isEmpty()) return geno;
        StringBuilder sb = new StringBuilder();
        for (String[] pair : pairs) {
            String a1 = pair[0], a2 = pair[1];
            boolean a1Upper = Character.isUpperCase(a1.charAt(0));
            boolean a2Upper = Character.isUpperCase(a2.charAt(0));
            if (a1Upper || a2Upper) {
                String domAllele = a1Upper ? a1 : a2;
                if (dominance.containsKey(domAllele)) {
                    sb.append(domAllele);  // dominant phenotype shown
                } else {
                    sb.append(a1).append(a2);  // codominant: show both
                }
            } else {
                sb.append(a1);  // homozygous recessive
            }
        }
        return sb.toString();
    }

    /**
     * Converts a genotype frequency map to a phenotype frequency map.
     */
    private Map<String, Double> applyDominance(Map<String, Double> genoFreqs) {
        Map<String, Double> phenoFreqs = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : genoFreqs.entrySet()) {
            String pheno = genotypeToPhenotype(e.getKey());
            phenoFreqs.merge(pheno, e.getValue(), Double::sum);
        }
        return phenoFreqs;
    }

    /** Retrieves stored genotype for an id, or null if not set. */
    private String getGenotype(String id) {
        Map<String, Object> m = fields.get("genotype");
        if (m == null) return null;
        Object v = m.get(id);
        return v != null ? v.toString() : null;
    }

    /** Canonical linkage key: alphabetically sorted "a,b". */
    private String linkageKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "," + b : b + "," + a;
    }

    /**
     * Applies linkage adjustment to gamete frequencies for a 2-locus dihybrid.
     * Parental combinations get (1−r)/2 each; recombinants get r/2 each.
     */
    private Map<String, Double> applyLinkage(Map<String, Double> gametes, double r) {
        if (gametes.size() != 4) return gametes;
        List<String> keys = new ArrayList<>(gametes.keySet());
        Map<String, Double> adj = new LinkedHashMap<>();
        adj.put(keys.get(0), (1 - r) / 2.0);   // parental AB
        adj.put(keys.get(1), r / 2.0);           // recombinant Ab
        adj.put(keys.get(2), r / 2.0);           // recombinant aB
        adj.put(keys.get(3), (1 - r) / 2.0);    // parental ab
        return adj;
    }

    /**
     * If the genotype has exactly 2 loci and any linkage is registered,
     * adjusts gamete frequencies accordingly.
     */
    private Map<String, Double> maybeApplyLinkage(Map<String, Double> gametes,
                                                   List<String[]> pairs) {
        if (pairs.size() != 2 || linkageMap.isEmpty()) return gametes;
        double r = linkageMap.values().iterator().next();
        return applyLinkage(gametes, r);
    }

    /**
     * Computes a self-cross distribution for a given genotype string.
     * Returns genotype → frequency map.
     */
    private Map<String, Double> selfCross(String geno) {
        List<String[]> pairs = parseGenotype(geno);
        if (pairs.isEmpty()) return Collections.emptyMap();
        Map<String, Double> g = generateGametes(pairs);
        return punnettSquare(g, g);
    }

    /**
     * Propagates one generation by self-crossing each genotype in the distribution.
     */
    private Map<String, Double> selfCrossDistribution(Map<String, Double> prev) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : prev.entrySet()) {
            Map<String, Double> offspring = selfCross(entry.getKey());
            for (Map.Entry<String, Double> o : offspring.entrySet()) {
                result.merge(o.getKey(), o.getValue() * entry.getValue(), Double::sum);
            }
        }
        return result;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Computations
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public Object visitFindExpr(FindExpr node) {
        Map<String, Object> m = fields.get(node.field);
        Object v = (m != null) ? m.get(node.id) : null;
        String genStr = node.generation != null ? " [generation " + node.generation + "]" : "";
        System.out.println("  find " + node.field + " " + node.id + genStr
                           + " → " + (v != null ? v : "(not set)"));
        return v;
    }

    // ── Cross ─────────────────────────────────────────────────────────────────

    @Override
    public Object visitCrossExpr(CrossExpr node) {
        String p1 = node.parent1, p2 = node.parent2, off = node.offspring;

        // Case 1: explicit ratios provided — record without Punnett
        if (node.ratios != null && !node.ratios.isEmpty()) {
            double sum = 0;
            List<Double> vals = new ArrayList<>();
            for (Expression e : node.ratios) {
                Object v = e.accept(this);
                double d = (v instanceof Number) ? ((Number) v).doubleValue() : 0;
                vals.add(d);
                sum += d;
            }
            final double total = sum == 0 ? 1 : sum;
            Map<String, Double> dist = new LinkedHashMap<>();
            for (int i = 0; i < vals.size(); i++) {
                dist.put("class" + (i + 1), vals.get(i) / total);
            }
            crossResults.put(off, dist);
            printDistribution("Cross [" + p1 + " x " + p2 + "] → " + off, dist, false);
            return null;
        }

        // Case 2: Punnett square from parent genotypes
        String g1 = getGenotype(p1);
        String g2 = getGenotype(p2);

        if (g1 == null || g2 == null) {
            System.out.printf("  Cross [%s x %s] → %s  (genotype unknown for %s)%n",
                    p1, p2, off, g1 == null ? p1 : p2);
            return null;
        }

        List<String[]> pairs1 = parseGenotype(g1);
        List<String[]> pairs2 = parseGenotype(g2);

        Map<String, Double> gametes1 = maybeApplyLinkage(generateGametes(pairs1), pairs1);
        Map<String, Double> gametes2 = maybeApplyLinkage(generateGametes(pairs2), pairs2);

        Map<String, Double> genoFreqs = punnettSquare(gametes1, gametes2);
        crossResults.put(off, genoFreqs);

        // Store modal genotype so find/probability can reference offspring later
        genoFreqs.entrySet().stream()
                 .max(Map.Entry.comparingByValue())
                 .ifPresent(e ->
                     fields.computeIfAbsent("genotype", k -> new LinkedHashMap<>())
                           .put(off, e.getKey())
                 );

        boolean isSexLinkedCross = pairs1.stream().anyMatch(pair ->
                sexLinked.stream().anyMatch(gene ->
                        gene.equalsIgnoreCase(String.valueOf(pair[0].charAt(0)))));

        System.out.printf("  Cross [%s x %s] → %s%n", p1, p2, off);

        // ── Step-by-step resolution ────────────────────────────────────────
        int step = 1;
        System.out.printf("    step: %d|Parse genotypes|%s = %s   %s = %s%n",
                step++, p1, g1, p2, g2);
        {
            StringBuilder s1 = new StringBuilder(), s2 = new StringBuilder();
            for (String g : gametes1.keySet()) { if (s1.length() > 0) s1.append(", "); s1.append(g); }
            for (String g : gametes2.keySet()) { if (s2.length() > 0) s2.append(", "); s2.append(g); }
            System.out.printf("    step: %d|Generate gametes|%s: [%s]   %s: [%s]%n",
                    step++, p1, s1, p2, s2);
        }
        System.out.printf("    step: %d|Build Punnett square|%dx%d = %d cells%n",
                step++, gametes1.size(), gametes2.size(), gametes1.size() * gametes2.size());
        if (!dominance.isEmpty()) {
            StringBuilder domStr = new StringBuilder();
            for (Map.Entry<String, String> e : dominance.entrySet()) {
                if (domStr.length() > 0) domStr.append("  ");
                domStr.append(e.getKey()).append(" → ").append(e.getValue());
            }
            System.out.printf("    step: %d|Apply dominance|%s%n", step++, domStr);
        }
        System.out.printf("    step: %d|Compute offspring ratios|%d unique genotype(s)%n",
                step, genoFreqs.size());

        // ── Punnett grid ────────────────────────────────────────────────────
        List<String> pCols = new ArrayList<>(gametes1.keySet());
        List<String> pRows = new ArrayList<>(gametes2.keySet());
        if (pCols.size() <= 4 && pRows.size() <= 4) {
            System.out.println("    punnett_cols: " + String.join(",", pCols));
            System.out.println("    punnett_rows: " + String.join(",", pRows));
            for (String rowG : pRows) {
                for (String colG : pCols) {
                    String geno = combineGametes(colG, rowG);
                    String pheno = genotypeToPhenotype(geno);
                    System.out.printf("    punnett_cell: %s|%s|%s|%s%n", colG, rowG, geno, pheno);
                }
            }
        }

        // ── Distributions ────────────────────────────────────────────────────
        printDistribution("  Genotype distribution", genoFreqs, true);

        Map<String, Double> phenoFreqs = applyDominance(genoFreqs);
        if (!phenoFreqs.equals(genoFreqs)) {
            printDistribution("  Phenotype distribution", phenoFreqs, true);
        }

        if (isSexLinkedCross) {
            System.out.printf("  (X-linked gene detected — male offspring are hemizygous)%n");
        }

        return null;
    }

    // ── Probability ───────────────────────────────────────────────────────────

    @Override
    public Object visitProbExpr(ProbExpr node) {
        boolean hasGiven = node.givenEvents != null && !node.givenEvents.isEmpty();

        if (!hasGiven) {
            for (Event ev : node.events) {
                printSimpleProb(ev);
            }
        } else {
            Event target = node.events.get(0);
            for (Event given : node.givenEvents) {
                printConditionalProb(target, given);
            }
        }
        return null;
    }

    private void printSimpleProb(Event ev) {
        Map<String, Double> dist = getOrComputeDist(ev.id, ev.kind);
        if (dist == null) {
            System.out.printf("  P(%s(%s)) = unknown (no genotype data)%n", ev.kind, ev.id);
            return;
        }
        System.out.printf("  P(%s(%s)):%n", ev.kind, ev.id);
        for (Map.Entry<String, Double> e : dist.entrySet()) {
            System.out.printf("    %-10s = %.3f%n", e.getKey(), e.getValue());
        }
    }

    private void printConditionalProb(Event target, Event given) {
        // We compute P(target | given) = P(target ∩ given) / P(given)
        // Both events refer to the same gene: compute phenotype distribution
        String id = target.id;
        Map<String, Double> genoDist = getOrComputeGenoDist(id);
        if (genoDist == null) {
            System.out.printf("  P(%s(%s) | %s(%s)) = unknown%n",
                    target.kind, target.id, given.kind, given.id);
            return;
        }

        // Compute P(given) and P(target AND given)
        double pGiven = 0.0, pBoth = 0.0;
        for (Map.Entry<String, Double> e : genoDist.entrySet()) {
            String geno = e.getKey();
            double freq = e.getValue();
            boolean satisfiesGiven  = genoMatchesEvent(geno, given);
            boolean satisfiesTarget = genoMatchesEvent(geno, target);
            if (satisfiesGiven)               pGiven += freq;
            if (satisfiesGiven && satisfiesTarget) pBoth += freq;
        }

        double result = pGiven > 0 ? pBoth / pGiven : 0.0;
        System.out.printf("  P(%s(%s) | %s(%s)) = %.3f%n",
                target.kind, target.id, given.kind, given.id, result);
    }

    /**
     * Returns the appropriate distribution for an event:
     *  - genotype event → raw genotype frequencies
     *  - phenotype event → phenotype frequencies (after dominance)
     */
    private Map<String, Double> getOrComputeDist(String id, String kind) {
        Map<String, Double> genoDist = getOrComputeGenoDist(id);
        if (genoDist == null) return null;
        if ("phenotype".equals(kind)) return applyDominance(genoDist);
        if ("genotype".equals(kind))  return genoDist;
        return genoDist;
    }

    /**
     * Gets or computes the genotype distribution for an id.
     * First checks crossResults; if not found, self-crosses the stored genotype.
     */
    private Map<String, Double> getOrComputeGenoDist(String id) {
        Map<String, Double> dist = crossResults.get(id);
        if (dist != null) return dist;
        String geno = getGenotype(id);
        if (geno == null) return null;
        dist = selfCross(geno);
        crossResults.put(id, dist);   // cache
        return dist;
    }

    /**
     * Returns true if the given genotype string satisfies the given event.
     * phenotype event → check if genotypeToPhenotype(geno) matches dominant/recessive expectation
     * genotype event  → genotype distribution (all genotypes satisfy "any genotype" event)
     */
    private boolean genoMatchesEvent(String geno, Event ev) {
        if ("phenotype".equals(ev.kind)) {
            // A genotype satisfies a "phenotype" event if it produces a dominant phenotype
            // (i.e., at least one dominant allele at every locus that has dominance info)
            String pheno = genotypeToPhenotype(geno);
            // Check if the phenotype contains at least one uppercase allele (dominant)
            for (char c : pheno.toCharArray()) {
                if (Character.isUpperCase(c)) return true;
            }
            return false;
        }
        if ("genotype".equals(ev.kind)) {
            return true;  // "genotype event" matches all genotypes for conditional P calc
        }
        return false;
    }

    // ── Linkage ───────────────────────────────────────────────────────────────

    @Override
    public Object visitLinkExpr(LinkExpr node) {
        if (node.ids == null || node.ids.size() < 2) return null;
        double r = 0.0;
        try { r = Double.parseDouble(node.recombination); } catch (Exception ignored) {}
        for (int i = 0; i < node.ids.size() - 1; i++) {
            for (int j = i + 1; j < node.ids.size(); j++) {
                linkageMap.put(linkageKey(node.ids.get(i), node.ids.get(j)), r);
            }
        }
        String distStr = node.distance != null ? node.distance + " cM" : "N/A";
        System.out.printf("  Linkage: [%s]  recombination = %.2f  distance = %s%n",
                String.join(" — ", node.ids), r, distStr);
        return null;
    }

    // ── Sex-linked ────────────────────────────────────────────────────────────

    @Override
    public Object visitSexExpr(SexExpr node) {
        sexLinked.add(node.id);
        if (node.field != null && node.value != null) {
            Object val = node.value.accept(this);
            fields.computeIfAbsent(node.field, k -> new LinkedHashMap<>()).put(node.id, val);
        }
        System.out.printf("  %s marked as sex-linked (X-linked inheritance)%n", node.id);
        return null;
    }

    // ── Blood group ───────────────────────────────────────────────────────────

    @Override
    public Object visitBloodExpr(BloodExpr node) {
        List<String> phenoInputs = new ArrayList<>();
        if (node.phenotypes != null) {
            for (Expression e : node.phenotypes) {
                Object v = e.accept(this);
                phenoInputs.add(v != null ? v.toString() : null);
            }
        }

        for (int i = 0; i < node.ids.size(); i++) {
            String id = node.ids.get(i);
            String genoStr = getGenotype(id);
            String phenoInput = i < phenoInputs.size() ? phenoInputs.get(i) : null;
            String bloodType;

            if ("ABO".equals(node.system)) {
                bloodType = determineABO(genoStr);
                if (bloodType == null && phenoInput != null) {
                    bloodType = "Type " + phenoInput;
                    fields.computeIfAbsent("phenotype", k -> new LinkedHashMap<>())
                          .put(id, phenoInput);
                }
                System.out.printf("  Blood group %-12s [ABO]: %s%n",
                        id, bloodType != null ? bloodType : "unknown (set genotype to determine)");
            } else {
                bloodType = determineRh(genoStr);
                if (bloodType == null && phenoInput != null) {
                    bloodType = phenoInput;
                    fields.computeIfAbsent("phenotype", k -> new LinkedHashMap<>())
                          .put(id, phenoInput);
                }
                System.out.printf("  Blood group %-12s [Rh]:  %s%n",
                        id, bloodType != null ? bloodType : "unknown (set genotype to determine)");
            }
        }

        // Blood cross Punnett for 2-parent scenarios
        if (node.ids.size() == 2) {
            String id1 = node.ids.get(0), id2 = node.ids.get(1);
            String geno1 = getGenotype(id1), geno2 = getGenotype(id2);
            if (geno1 != null && geno2 != null) {
                boolean isABO = "ABO".equals(node.system);
                Map<String, Double> gam1, gam2;
                if (isABO) {
                    gam1 = aboGametes(geno1);
                    gam2 = aboGametes(geno2);
                } else {
                    gam1 = generateGametes(parseGenotype(geno1));
                    gam2 = generateGametes(parseGenotype(geno2));
                }
                if (!gam1.isEmpty() && !gam2.isEmpty()) {
                    printBloodCross(id1, id2, geno1, geno2, gam1, gam2, isABO);
                }
            }
        }
        return null;
    }

    /** ABO blood type from genotype string (e.g. "IAi", "IAIB", "IBIB", "ii"). */
    private String determineABO(String geno) {
        if (geno == null) return null;
        boolean hasIA = geno.contains("IA");
        boolean hasIB = geno.contains("IB");
        boolean isOO  = geno.equals("ii");
        if (hasIA && hasIB) return "Type AB  (I^A I^B — codominant)";
        if (hasIA) return "Type A  (" + (geno.contains("i") ? "I^A i" : "I^A I^A") + ")";
        if (hasIB) return "Type B  (" + (geno.contains("i") ? "I^B i" : "I^B I^B") + ")";
        if (isOO)  return "Type O  (ii — homozygous recessive)";
        return null;
    }

    /** Rh blood type from genotype string ("DD", "Dd", "dd"). */
    private String determineRh(String geno) {
        if (geno == null) return null;
        if (geno.equals("DD")) return "Rh+  (DD — homozygous dominant)";
        if (geno.equals("Dd")) return "Rh+  (Dd — heterozygous carrier)";
        if (geno.equals("dd")) return "Rh−  (dd — homozygous recessive)";
        if (geno.contains("D")) return "Rh+  (D present)";
        return null;
    }

    /** Generates gametes for an ABO genotype (handles 2-char alleles IA, IB). */
    private Map<String, Double> aboGametes(String geno) {
        if (geno == null) return Collections.emptyMap();
        Map<String, Double> g = new LinkedHashMap<>();
        if (geno.equals("IAIB")) { g.put("IA", 0.5); g.put("IB", 0.5); }
        else if (geno.equals("IAIA")) { g.put("IA", 1.0); }
        else if (geno.equals("IBIB")) { g.put("IB", 1.0); }
        else if (geno.equals("ii"))   { g.put("i",  1.0); }
        else if (geno.contains("IA") && geno.contains("i")) { g.put("IA", 0.5); g.put("i", 0.5); }
        else if (geno.contains("IB") && geno.contains("i")) { g.put("IB", 0.5); g.put("i", 0.5); }
        return g;
    }

    /** Canonical ABO offspring genotype from two gametes (IA > IB > i). */
    private String aboOffspring(String a1, String a2) {
        int r1 = "IA".equals(a1) ? 0 : "IB".equals(a1) ? 1 : 2;
        int r2 = "IA".equals(a2) ? 0 : "IB".equals(a2) ? 1 : 2;
        return r1 <= r2 ? a1 + a2 : a2 + a1;
    }

    /** ABO phenotype letter from two gametes. */
    private String aboPhenoFromGametes(String a1, String a2) {
        boolean ia = "IA".equals(a1) || "IA".equals(a2);
        boolean ib = "IB".equals(a1) || "IB".equals(a2);
        if (ia && ib) return "AB";
        if (ia) return "A";
        if (ib) return "B";
        return "O";
    }

    /** Prints a Punnett-grid blood cross between two parents. */
    private void printBloodCross(String id1, String id2, String geno1, String geno2,
                                  Map<String, Double> gam1, Map<String, Double> gam2,
                                  boolean isABO) {
        System.out.printf("  Blood cross [%s x %s]:%n", id1, id2);
        int step = 1;
        System.out.printf("    step: %d|Identify genotypes|%s = %s   %s = %s%n",
                step++, id1, geno1, id2, geno2);
        StringBuilder s1 = new StringBuilder(), s2 = new StringBuilder();
        for (String g : gam1.keySet()) { if (s1.length() > 0) s1.append(", "); s1.append(g); }
        for (String g : gam2.keySet()) { if (s2.length() > 0) s2.append(", "); s2.append(g); }
        System.out.printf("    step: %d|Generate gametes|%s: [%s]   %s: [%s]%n",
                step++, id1, s1, id2, s2);
        System.out.printf("    step: %d|Build Punnett square|%dx%d = %d cells%n",
                step++, gam1.size(), gam2.size(), gam1.size() * gam2.size());
        System.out.printf("    step: %d|Determine blood type per offspring genotype|%n", step);
        List<String> pCols = new ArrayList<>(gam1.keySet());
        List<String> pRows = new ArrayList<>(gam2.keySet());
        System.out.println("    punnett_cols: " + String.join(",", pCols));
        System.out.println("    punnett_rows: " + String.join(",", pRows));
        Map<String, Double> bloodDist = new LinkedHashMap<>();
        for (String rowG : pRows) {
            for (String colG : pCols) {
                String offGeno, offPheno;
                if (isABO) {
                    offGeno  = aboOffspring(colG, rowG);
                    offPheno = aboPhenoFromGametes(colG, rowG);
                } else {
                    offGeno  = combineGametes(colG, rowG);
                    offPheno = offGeno.contains("D") ? "Rh+" : "Rh-";
                }
                System.out.printf("    punnett_cell: %s|%s|%s|%s%n", colG, rowG, offGeno, offPheno);
                bloodDist.merge(offPheno, gam1.get(colG) * gam2.get(rowG), Double::sum);
            }
        }
        printDistribution(isABO ? "  ABO type distribution" : "  Rh type distribution",
                bloodDist, true);
    }

    // ── Prediction ────────────────────────────────────────────────────────────

    @Override
    public Object visitPredExpr(PredExpr node) {
        int gen = node.generation != null ? node.generation : 1;
        for (String id : node.ids) {
            String geno = getGenotype(id);
            if (geno == null) {
                System.out.printf("  Pred %-12s generation %d: no genotype data%n", id, gen);
                continue;
            }
            Map<String, Double> dist = selfCross(geno);
            for (int g = 2; g < gen; g++) {
                dist = selfCrossDistribution(dist);
            }
            Map<String, Double> phenoDist = applyDominance(dist);
            System.out.printf("  Pred %s  generation %d:%n", id, gen);
            printDistribution("    genotype ", dist, true);
            printDistribution("    phenotype", phenoDist, true);
        }
        return null;
    }

    // ── Estimate ──────────────────────────────────────────────────────────────

    @Override
    public Object visitEstimateExpr(EstimateExpr node) {
        double p = 0.0;
        try { p = Double.parseDouble(node.value); } catch (Exception ignored) {}
        double conf = 0.95;
        if (node.confidence != null) {
            try { conf = Double.parseDouble(node.confidence); } catch (Exception ignored) {}
        }
        double z  = zForConfidence(conf);
        double se = Math.sqrt(p * (1 - p) / 100.0);  // n = 100 assumed
        double lo = Math.max(0.0, p - z * se);
        double hi = Math.min(1.0, p + z * se);
        System.out.printf("  Estimate %-12s %.4f  (CI %.4f–%.4f at %.0f%% confidence)%n",
                node.id + ":", p, lo, hi, conf * 100);
        return null;
    }

    private double zForConfidence(double conf) {
        if (conf >= 0.99) return 2.576;
        if (conf >= 0.95) return 1.960;
        return 1.645;  // default 90%
    }

    // ── Infer ─────────────────────────────────────────────────────────────────

    @Override
    public Object visitInferExpr(InferExpr node) {
        if (node.inferParents) {
            String offGeno = getGenotype(node.sourceId);
            if (offGeno == null) {
                System.out.printf("  Infer parents of %s: no genotype data%n", node.sourceId);
                return null;
            }
            List<String[]> pairs = parseGenotype(offGeno);
            System.out.printf("  Infer parents of %s  (genotype: %s):%n",
                    node.sourceId, offGeno);
            for (String[] pair : pairs) {
                System.out.printf("    Locus %s/%s:  each parent contributed one allele (%s or %s)%n",
                        pair[0], pair[1], pair[0], pair[1]);
                // Possible parent genotypes at this locus
                String a1 = pair[0], a2 = pair[1];
                if (a1.equals(a2)) {
                    System.out.printf("      → offspring is homozygous; parents may be %s%s or heterozygous%n",
                            a1, a2);
                } else {
                    System.out.printf("      → possible parent genotypes: %s%s × %s%s, or %s%s × %s%s%n",
                            a1, a1, a2, a2, a1, a2, a1, a2);
                }
            }
        } else {
            List<String> ids = new ArrayList<>();
            ids.add(node.sourceId);
            if (node.additionalIds != null) ids.addAll(node.additionalIds);
            System.out.printf("  Infer %s from: %s%n", node.field, String.join(", ", ids));
            Map<String, Object> fm = fields.get(node.field);
            for (String id : ids) {
                Object val = (fm != null) ? fm.get(id) : null;
                System.out.printf("    %-15s %s = %s%n",
                        id + ".", node.field, val != null ? val : "(not set)");
            }
        }
        return null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Events
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public Object visitEvent(Event node) {
        if (node.alleles != null && !node.alleles.isEmpty()) {
            return node.kind + "(" + node.id + ", " + String.join(", ", node.alleles) + ")";
        }
        return node.kind + "(" + node.id + ")";
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Print
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public Object visitPrintStatement(PrintStatement node) {
        // 1. "print field id" or "print field all expr"
        if (node.field != null) {
            Map<String, Object> m = fields.get(node.field);
            if (node.targetId != null) {
                Object v = (m != null) ? m.get(node.targetId) : null;
                System.out.println(v != null ? v : "(not set)");
                return null;
            }
            if (node.printAll && node.expressions != null) {
                // "print field all expr" — filter by expression value
                Object filterVal = node.expressions.get(0).accept(this);
                if (m != null) {
                    for (Map.Entry<String, Object> e : m.entrySet()) {
                        if (Objects.equals(String.valueOf(e.getValue()),
                                           String.valueOf(filterVal))) {
                            System.out.println(e.getKey() + " → " + e.getValue());
                        }
                    }
                }
                return null;
            }
        }

        // 2. "print id" (field=null, targetId set)
        if (node.targetId != null) {
            Object v = resolveId(node.targetId);
            System.out.println(v);
            return null;
        }

        // 3. "print exprList"
        if (node.expressions != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < node.expressions.size(); i++) {
                Object v = node.expressions.get(i).accept(this);
                if (v instanceof Event) v = visitEvent((Event) v);
                if (i > 0) sb.append("  ");
                sb.append(v);
            }
            System.out.println(sb);
            return null;
        }

        // 4. "print eventList"
        if (node.events != null) {
            for (Event e : node.events) {
                System.out.println(visitEvent(e));
            }
        }
        return null;
    }

    /**
     * Resolves an identifier to its stored value using priority:
     * "value" → "phenotype" → "genotype" → any other field → id itself.
     */
    private Object resolveId(String id) {
        String[] priority = {"value", "phenotype", "genotype"};
        for (String f : priority) {
            Map<String, Object> m = fields.get(f);
            if (m != null && m.containsKey(id)) return m.get(id);
        }
        for (Map.Entry<String, Map<String, Object>> e : fields.entrySet()) {
            if (e.getValue().containsKey(id)) return e.getValue().get(id);
        }
        return id;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Utility helpers
    // ═════════════════════════════════════════════════════════════════════════

    private boolean isTruthy(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number)  return ((Number) v).doubleValue() != 0;
        return v != null;
    }

    private boolean compareObjects(Object l, String op, Object r) {
        if (l instanceof Number && r instanceof Number) {
            double ld = ((Number) l).doubleValue(), rd = ((Number) r).doubleValue();
            switch (op) {
                case "<":  return ld <  rd;
                case "<=": return ld <= rd;
                case ">":  return ld >  rd;
                case ">=": return ld >= rd;
                case "==": return ld == rd;
                case "!=": return ld != rd;
            }
        }
        if (l instanceof String && r instanceof String) {
            int c = ((String) l).compareTo((String) r);
            switch (op) {
                case "<":  return c <  0;
                case "<=": return c <= 0;
                case ">":  return c >  0;
                case ">=": return c >= 0;
                case "==": return c == 0;
                case "!=": return c != 0;
            }
        }
        boolean eq = Objects.equals(l, r);
        return "==".equals(op) ? eq : !eq;
    }

    /**
     * Prints a named distribution table.
     * If inline=true: prints on one line as "label  |  key: XX.X%  |  ..."
     * If inline=false: prints key/value pairs on separate lines.
     */
    private void printDistribution(String label, Map<String, Double> dist, boolean inline) {
        if (dist.isEmpty()) return;
        if (inline) {
            StringBuilder sb = new StringBuilder("  ").append(label).append("  |");
            for (Map.Entry<String, Double> e : dist.entrySet()) {
                sb.append(String.format("  %-8s %.1f%%  |", e.getKey() + ":", e.getValue() * 100));
            }
            System.out.println(sb);
        } else {
            System.out.println("  " + label);
            for (Map.Entry<String, Double> e : dist.entrySet()) {
                System.out.printf("    %-10s %.3f%n", e.getKey(), e.getValue());
            }
        }
    }
}
