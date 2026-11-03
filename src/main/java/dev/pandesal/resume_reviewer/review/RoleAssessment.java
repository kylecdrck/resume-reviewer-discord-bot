package dev.pandesal.resume_reviewer.review;

/**
 * The LLM's suitability assessment for a single requested job role.
 *
 * @param role      the requested role
 * @param score     0-100 suitability score
 * @param reasoning short explanation of the score
 */
public record RoleAssessment(String role, double score, String reasoning) {
}