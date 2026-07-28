package config;

/**
 * One level-based EXP rate checkpoint (this fork). Bound from config.yaml by yamlbeans
 * (public fields, exact name match) as elements of {@code server.LEVEL_EXP_TIERS}.
 *
 * <p>{@code maxLevel} is inclusive: the first tier (scanning ascending) whose {@code maxLevel}
 * is >= the player's level supplies that player's mob EXP multiplier. A player above every
 * tier's {@code maxLevel} falls back to the last (highest) tier.
 */
public class LevelExpTier {
    public int maxLevel;
    public int expRate;
}
