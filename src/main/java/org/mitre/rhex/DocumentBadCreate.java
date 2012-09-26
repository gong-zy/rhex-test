package org.mitre.rhex;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HTTP;
import org.mitre.test.Context;
import org.mitre.test.TestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

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
 * Status Code: 201, <B>400</B>
 * </pre>
 *
 * @author Jason Mathews, MITRE Corp.
 * Date: 2/20/12 10:45 AM
 */
public class DocumentBadCreate extends DocumentCreate {

	@NonNull
	public String getId() {
		return "6.4.2.4";
	}

	@Override
	public boolean isRequired() {
		return true;
	}

	@NonNull
	public String getName() {
		return "POST operation on baseURL/sectionpath with invalid content returns 400 status code";
	}

	/*
	NOTE: getDependencyClasses() inherited from super-class

	@NonNull
	public List<Class<? extends TestUnit>> getDependencyClasses() {
		return Collections.<Class<? extends TestUnit>> singletonList(BaseSectionFromRootXml.class); // 6.4.1.1
	}
 	*/

	protected void sendRequest(Context context) throws TestException {
		final HttpClient client = context.getHttpClient();
		try {
			URI baseUrl = context.getBaseURL(sectionPath);
			if (log.isDebugEnabled()) {
				System.out.println("URL: " + baseUrl);
			}
			/*
			MultipartEntity reqEntity = new MultipartEntity();
			reqEntity.addPart("content", new StringBody("this is not XML"));
			reqEntity.addPart("metadata", new StringBody("metadata"));
			post.setEntity(reqEntity);
			*/
			//List<NameValuePair> formParams = new ArrayList<NameValuePair>(1);
			//formParams.add(new BasicNameValuePair("content", "this is not XML"));
			HttpPost post = getRequest(baseUrl);
			//post.setEntity(new UrlEncodedFormEntity(formParams));
			System.out.println("executing request " + post.getRequestLine());
			HttpResponse response = context.executeRequest(client, post);
			int code = response.getStatusLine().getStatusCode();
			if (log.isDebugEnabled()) {
				System.out.println("----------------------------------------");
                dumpResponse(post, response);
			}

			if (code == 400) {
				setStatus(StatusEnumType.SUCCESS);
			} else if (code == 406) {
				setStatus(StatusEnumType.SUCCESS);
				addLogWarning("Expected code is 400 but 406 is allowed");
			} else {
				if (!log.isDebugEnabled()) {
					System.out.println("URL: " + baseUrl);
					dumpResponse(post, response);
				}
				setStatus(StatusEnumType.FAILED, "Expected 400 HTTP status code but was: " + code);
			}

		} catch (IOException e) {
			throw new TestException(e);
		} catch (URISyntaxException e) {
			throw new TestException(e);
		} finally {
			client.getConnectionManager().shutdown();
		}
	}

	protected HttpPost getRequest(URI baseUrl) {
		HttpPost post = new HttpPost(baseUrl);
		StringEntity entity = new StringEntity("plain text", ContentType.TEXT_PLAIN);
		post.setEntity(entity);
		return post;
	}


}