package dev.pandesal.resume_reviewer.pdf;

import java.io.File;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Extracts resume text from a PDF (PDFBox) and cleans it with
 * {@link TextSanitizer} to strip unrecognizable characters and symbols.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeTextExtractor {

	private final PdfTextExtractor pdfTextExtractor;
	private final TextSanitizer textSanitizer;

	/**
	 * @param file a resume PDF
	 * @return the extracted, sanitized text (may be empty for scanned PDFs
	 *         without a text layer)
	 * @throws IOException if the file cannot be read or is not a PDF
	 */
	public String extract(File file) throws IOException {
		if (!file.getName().toLowerCase().endsWith(".pdf")) {
			throw new IOException("Unsupported file type: " + file.getName() + " (expected PDF)");
		}
		return textSanitizer.sanitize(pdfTextExtractor.extract(file));
	}
}
