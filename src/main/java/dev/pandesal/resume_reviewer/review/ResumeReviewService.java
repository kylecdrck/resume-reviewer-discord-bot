package dev.pandesal.resume_reviewer.review;

import dev.pandesal.resume_reviewer.domain.Review;
import dev.pandesal.resume_reviewer.pdf.FileHasher;
import dev.pandesal.resume_reviewer.pdf.ResumeTextExtractor;
import dev.pandesal.resume_reviewer.redaction.ResumeRedactor;
import dev.pandesal.resume_reviewer.repository.ReviewRepository;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the review workflow:
 * <ul>
 *   <li>{@link #review(File, boolean, List)} — extract text, evaluate via the
  *       LLM, and (when {@code save} is true) persist the evaluation report
  *       keyed by the original file hash. A previously stored report for the
  *       same hash is returned without re-calling the LLM. The stored resume
  *       text is redacted of PII; the hash is always computed from the original
  *       file.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeReviewService {

	private final ResumeTextExtractor resumeTextExtractor;
	private final FileHasher fileHasher;
	private final ResumeRedactor resumeRedactor;
	private final LlmAtsEvaluator llmAtsEvaluator;
	private final ReviewRepository reviewRepository;

	/**
	 * Reviews a resume PDF via the LLM. When {@code save} is true, the
	 * evaluation report is persisted (keyed by the original file hash) for
	 * future reference.
	 *
	 * @param pdf  the resume PDF file
	 * @param save whether to store the evaluation report
	 * @return the evaluation result
	 * @throws IOException if the file cannot be read
	 */
	public ReviewResult review(File pdf, boolean save) throws IOException {
			return review(pdf, save, List.of()).result();
		}

		/**
		 * Reviews a resume PDF via the LLM. When {@code save} is true, the
		 * evaluation report is persisted (keyed by the original file hash) for
		 * future reference.
		 *
		 * <p>The returned {@link ReviewOutcome} reports whether the evaluation was
		 * fresh; a stored evaluation for the same file hash is returned without
		 * re-calling the LLM.
		 *
		 * @param pdf   the resume PDF file
		 * @param save  whether to store the evaluation report
		 * @param roles the requested roles (informational; the role suitability
		 *              analysis itself is a separate LLM call)
		 * @return the evaluation result and whether it was freshly evaluated
		 * @throws IOException if the file cannot be read
		 */
		public ReviewOutcome review(File pdf, boolean save, List<String> roles) throws IOException {
			long start = System.currentTimeMillis();
			log.info("Review started for {} (save={}, roles={})", pdf.getName(), save, roles);

			String text = resumeTextExtractor.extract(pdf);
			if (text == null || text.isBlank()) {
				throw new IllegalArgumentException(
					"No extractable text found. This file may be blank or unreadable (scanned PDFs without a text layer are not OCR'd).");
			}
			log.info("Extracted {} chars from {}", text.length(), pdf.getName());
			String fileHash = fileHasher.hash(pdf);

			// Cache: return the stored evaluation if this file was already evaluated and saved.
			var existing = reviewRepository.findByFileHash(fileHash);
			if (existing.isPresent()) {
				Review r = existing.get();
				log.info("Returning stored evaluation for {} (hash {})", pdf.getName(), fileHash);
				return new ReviewOutcome(new ReviewResult(r.getOverallScore(), r.getDetailedRatings(),
					r.getCriticalBlockers(), r.getStrengths(), r.getRecommendations(), r.getReport()), false);
			}

			log.info("Requesting LLM evaluation for {}", pdf.getName());
			ReviewResult result = llmAtsEvaluator.evaluate(text);
			log.info("LLM evaluation ready for {} (score {})", pdf.getName(), result.overallScore());

			if (save) {
				storeEvaluation(fileHash, resumeRedactor.redact(text), result);
			}
			else {
				log.info("save disabled; not storing evaluation for {} (hash {})", pdf.getName(), fileHash);
			}

			log.info("Review complete for {} -> score {} in {} ms", pdf.getName(), result.overallScore(),
				System.currentTimeMillis() - start);
			return new ReviewOutcome(result, true);
		}

	/**
		 * Extracts the sanitized resume text from a PDF.
		 *
		 * @param pdf the resume PDF file
		 * @return the extracted text
		 * @throws IOException if the file cannot be read
		 */
		public String extractText(File pdf) throws IOException {
			return resumeTextExtractor.extract(pdf);
		}

		/**
		 * Persists an evaluation report keyed by the original file hash. The
		 * stored resume text is the redacted version.
		 */
	@Transactional
	public void storeEvaluation(String fileHash, String redactedText, ReviewResult result) {
		Review review = new Review();
		review.setFileHash(fileHash);
		review.setResumeTextBase64(Base64.getEncoder().encodeToString(redactedText.getBytes(StandardCharsets.UTF_8)));
		review.setReport(result.report());
		review.setOverallScore(result.overallScore());
		review.setDetailedRatings(result.detailedRatings());
		review.setCriticalBlockers(result.criticalBlockers());
		review.setStrengths(result.strengths());
		review.setRecommendations(result.recommendations());
		review.setCreatedAt(Instant.now());
		reviewRepository.save(review);
		log.info("Stored evaluation for hash {}", fileHash);
	}
}
