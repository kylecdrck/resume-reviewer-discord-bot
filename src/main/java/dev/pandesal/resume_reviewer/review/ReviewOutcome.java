package dev.pandesal.resume_reviewer.review;

/**
 * The result of a review plus whether it was freshly evaluated.
 *
 * @param result the ATS evaluation result
 * @param fresh  true when the LLM evaluated the resume in this call; false
 *               when a stored evaluation was returned from the cache
 */
public record ReviewOutcome(ReviewResult result, boolean fresh) {
}