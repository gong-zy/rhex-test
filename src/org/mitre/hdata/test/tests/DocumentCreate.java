package org.mitre.hdata.test.tests;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.util.EntityUtils;
import org.mitre.hdata.test.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Test for section document creation
 *
 * <pre>
 *  6.4 baseURL/sectionpath
 *
 *  6.4.2.2 POST Add new document
 *
 * When adding a new section document, the request Content Type MUST be �multipart/form-data�
 * if including metadata. In this case, the content part MUST contain the section document.
 * The content part MUST include a Content-Disposition header with a disposition of �form-data�
 * and a name of �content.� The metadata part MUST contain the metadata for this section document.
 * The metadata part MUST include a Content-Disposition header with a disposition of �form-data�
 * and a name of �metadata.� It is to be treated as informational, since the service MUST compute
 * the valid new metadata based on the requirements found in the HRF specification. The content
 * media type MUST conform to the media type of either the section or the media type identified
 * by metadata of the section document. For XML media types, the document MUST also conform to
 * the XML schema identified by the extensionId for the section or the document metadata.
 *
 * If the content cannot be validated against the media type and the XML schema identified
 * by the content type of this section, the server MUST return a status code of 400.
 *
 * If the request is successful, the new section document MUST show up in the document
 * feed for the section. The server returns a 201 with a Location header containing
 * the URI of the new document.
 *
 * Status Code: 201, 400
 * </pre>
 *
 * @author Jason Mathews, MITRE Corp.
 * Date: 2/20/12 10:45 AM
 */
public class DocumentCreate extends BaseTest {

	private static final Logger log = LoggerFactory.getLogger(DocumentCreate.class);

	@NonNull
	@Override
	public String getId() {
		return "6.4.2.2";
	}

	@Override
	public boolean isRequired() {
		return true;
	}

	@NonNull
	public String getName() {
		return "POST operation on baseURL/sectionpath adds a new Document";
	}

	@NonNull
	public List<Class<? extends TestUnit>> getDependencyClasses() {
		return Collections.<Class<? extends TestUnit>> singletonList(BaseUrlRootXml.class); // 6.3.1.1
	}

	public void execute() throws TestException {
		// pre-conditions: for this test to be executed the prerequisite test BaseUrlRootXml must have passed
		// with 200 HTTP response and valid root.xml content.
		TestUnit baseTest = getDependency(BaseUrlRootXml.class);
		if (baseTest == null) {
			// assertion failed: this should never be null
			log.error("Failed to retrieve prerequisite test: BaseUrlRootXml");
			setStatus(StatusEnumType.SKIPPED, "Failed to retrieve prerequisite test: 6.3.1.1");
			return;
		}
		Map<String, String> extensionPathMap = ((BaseUrlRootXml)baseTest).getExtensionPathMap();
		if (extensionPathMap.isEmpty()) {
			log.error("Failed to retrieve prerequisite test results: BaseUrlRootXml");
			setStatus(StatusEnumType.SKIPPED, "Failed to retrieve prerequisite test results: 6.3.1.1");
			return;
		}
		final Context context = Loader.getInstance().getContext();
		String extension = context.getString("document.extension");
		if (StringUtils.isBlank(extension)) {
			// check pre-conditions and setup
			log.error("Failed to specify valid section extension property in configuration");
			setStatus(StatusEnumType.SKIPPED, "Failed to specify valid section extension property in configuration");
			return;
		}
		/*
		expecting:

		 <?xml version="1.0" encoding="UTF-8"?>
		 <root xmlns="http://projecthdata.org/hdata/schemas/2009/06/core" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
			<extensions>
				<extension extensionId="1">http://projecthdata.org/extension/c32</extension>
				<extension extensionId="2">http://projecthdata.org/hdata/schemas/2009/06/allergy</extension>
				...
			</extensions>
			<sections>
    			<section path="c32" name="C32" extensionId="1"/>
    			<section path="allergies" name="Allergies" extensionId="2"/>
    			...
			</sections>
		 </root>
		 */

		String sectionPath = extensionPathMap.get(extension);
		if (sectionPath == null) {
			log.error("Failed to find section " + extension + " in root.xml");
			setStatus(StatusEnumType.SKIPPED, "Failed to find section in test results");
			return;
		}

		System.out.println("section path: " + sectionPath);
		sendRequest(context, sectionPath);
	}

	protected void sendRequest(Context context, String sectionPath) throws TestException {
		String fileName = context.getString("document.file");
		File fileToUpload = new File(fileName);
		if (StringUtils.isBlank(fileName) || !fileToUpload.isFile()) {
			// check pre-conditions and setup
			log.error("Failed to specify valid document file property in configuration");
			setStatus(StatusEnumType.SKIPPED, "Failed to specify valid document file property in configuration");
			return;
		}
		final HttpClient client = context.getHttpClient();
		try {
			URI baseUrl = context.getBaseURL(sectionPath);
			if (log.isDebugEnabled()) {
				System.out.println("\nURL: " + baseUrl);
			}
			FileEntity reqEntity = new FileEntity(fileToUpload, MIME_APPLICATION_XML);
			/*
			MultipartEntity reqEntity = new MultipartEntity();
			FileBody fileBody = new FileBody(fileToUpload, MIME_APPLICATION_XML);
			reqEntity.addPart("content", fileBody);
			*/
			// reqEntity.addPart("metadata", new StringBody(fileToUpload.getName())); // should be a separate XML profile file ??
			HttpPost post = new HttpPost(baseUrl);
			post.setEntity(reqEntity);
			System.out.println("executing request " + post.getRequestLine());
			HttpResponse response = client.execute(post);
			int code = response.getStatusLine().getStatusCode();
			if (log.isDebugEnabled()) {
				System.out.println("----------------------------------------");
				System.out.println("POST Response status=" + code);
				for (Header header : response.getAllHeaders()) {
					System.out.println("\t" + header.getName() + ": " + header.getValue());
				}
				HttpEntity resEntity = response.getEntity();
				if (resEntity != null) {
					System.out.println("----------------------------------------");
					System.out.println(EntityUtils.toString(resEntity));
				}
			}
			if (code != 201) {
				setStatus(StatusEnumType.FAILED, "Expected 201 HTTP status code but was: " + code);
				return;
			}
			// TODO: need to verify document is now added to the section ATOM feed in another test
			setStatus(StatusEnumType.SUCCESS);
		} catch (IOException e) {
			throw new TestException(e);
		} catch (URISyntaxException e) {
			throw new TestException(e);
		} finally {
			client.getConnectionManager().shutdown();
		}
	}

}