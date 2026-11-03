package dev.pandesal.resume_reviewer.pdf;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.RandomAccessStreamCacheImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Extracts the text content of a PDF file using Apache PDFBox.
 *
 * <p>Untrusted input is bounded on three axes so a small file cannot exhaust
 * memory, CPU, or the LLM token budget: the content must start with the
 * {@code %PDF-} magic bytes, the document may not exceed {@link #MAX_PAGES}
 * pages, and the extracted text is truncated to {@link #MAX_TEXT_CHARS}
 * characters.
 */
@Slf4j
@Component
public class PdfTextExtractor {

	/** Maximum number of pages processed; a longer document is rejected. */
	public static final int MAX_PAGES = 30;

	/** Maximum number of extracted characters; the rest are dropped. */
	public static final int MAX_TEXT_CHARS = 60_000;

	/** Heap budget PDFBox may use before spilling to a temp file. */
	private static final long MAX_MAIN_MEMORY_BYTES = 64L * 1024 * 1024;

	private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	/**
	 * @param file the PDF file to read
	 * @return the extracted text (may be empty for scanned/image-only PDFs),
	 *         truncated to {@link #MAX_TEXT_CHARS}
	 * @throws IOException if the file cannot be read, is not a PDF, or has too
	 *         many pages
	 */
	public String extract(File file) throws IOException {
		requirePdfMagic(file);
		try (PDDocument document = Loader.loadPDF(file, streamCache())) {
			int pages = document.getNumberOfPages();
			if (pages > MAX_PAGES) {
				throw new IOException("PDF has too many pages (" + pages + ", max " + MAX_PAGES + ").");
			}
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setEndPage(Math.min(pages, MAX_PAGES));
			String text = stripper.getText(document);
			if (text == null) {
				return "";
			}
			if (text.length() > MAX_TEXT_CHARS) {
				log.debug("Truncating extracted text from {} to {} characters", text.length(), MAX_TEXT_CHARS);
				text = text.substring(0, MAX_TEXT_CHARS);
			}
			log.debug("Extracted {} characters from {}", text.length(), file.getName());
			return text;
		}
	}

	/**
	 * @param file the file to inspect
	 * @return true if the file starts with the {@code %PDF-} magic bytes
	 * @throws IOException if the file cannot be read
	 */
	public static boolean hasPdfMagic(File file) throws IOException {
		byte[] head = new byte[PDF_MAGIC.length];
		try (InputStream in = Files.newInputStream(file.toPath())) {
			if (in.readNBytes(head, 0, head.length) < PDF_MAGIC.length) {
				return false;
			}
		}
		for (int i = 0; i < PDF_MAGIC.length; i++) {
			if (head[i] != PDF_MAGIC[i]) {
				return false;
			}
		}
		return true;
	}

	private static void requirePdfMagic(File file) throws IOException {
		if (!hasPdfMagic(file)) {
			throw new IOException("Not a PDF file (missing %PDF- header).");
		}
	}

		/**
		 * @return a stream cache that keeps up to {@link #MAX_MAIN_MEMORY_BYTES} in
		 *         memory and spills the rest to a temp file
		 */
		private static RandomAccessStreamCache.StreamCacheCreateFunction streamCache() {
			return () -> new RandomAccessStreamCacheImpl();
		}
	}
