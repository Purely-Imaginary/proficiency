package dev.amman.proficiency.xp;

/** The rule that won for a subject, and what the subject's own numbers make of its XP spec. */
public record XpMatch(XpRule rule, XpSubject subject) {

    /** The XP of the rule for this subject, or 0 when the rule pays nothing. */
    public double xp() {
        if (rule.paysNothing || rule.xp == null) {
            return 0;
        }
        return rule.xp.eval(subject.hardness(), subject.maxHealth());
    }

    public boolean has(String flag) {
        return rule.has(flag);
    }

    /** True when the winning rule says "pay nothing" or has no skill. */
    public boolean paysNothing() {
        return rule.paysNothing || rule.skill == null;
    }
}
