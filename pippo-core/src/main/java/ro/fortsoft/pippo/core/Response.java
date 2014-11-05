/*
 * Copyright 2014 Decebal Suiu
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with
 * the License. You may obtain a copy of the License in the LICENSE file, or at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package ro.fortsoft.pippo.core;

import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Decebal Suiu
 */
public class Response {

    private static final Logger log = LoggerFactory.getLogger(Response.class);

    private HttpServletResponse httpServletResponse;
    private TemplateEngine templateEngine;
    private Map<String, Object> locals;

    Response(HttpServletResponse httpServletResponse, TemplateEngine templateEngine) {
        this.httpServletResponse = httpServletResponse;
        this.templateEngine = templateEngine;
    }

    public String getContentType() {
        return httpServletResponse.getContentType();
    }

    public Response contentType(String contentType) {
        httpServletResponse.getContentType();

        return this;
    }

    public Response characterEncoding(String charset) {
        getHttpServletResponse().setCharacterEncoding(charset);

        return this;
    }

    public String getCharacterEncoding() {
        return getHttpServletResponse().getCharacterEncoding();
    }

    public Response contentLength(long length) {
        httpServletResponse.addHeader(HttpConstants.Header.CONTENT_LENGTH, Long.toString(length));

        return this;
    }

    public Response header(String name, String value) {
        httpServletResponse.addHeader(name, value);

        return this;
    }

    /**
     * A browser redirect.
     *
     * @param location Where to redirect
     */
    public void redirect(String location) {
        try {
            httpServletResponse.sendRedirect(location);
        } catch (IOException e) {
            throw new PippoRuntimeException(e);
        }
    }

    /**
     * A permanent (3XX status code) redirect.
     */
    public void redirect(String location, int statusCode) {
        httpServletResponse.setStatus(statusCode);
        httpServletResponse.setHeader(HttpConstants.Header.LOCATION, location);
        httpServletResponse.setHeader(HttpConstants.Header.CONNECTION, "close");
        try {
            httpServletResponse.sendError(statusCode);
        } catch (IOException e) {
            throw new PippoRuntimeException(e);
        }
    }

    public Response cookie(String name, String value) {
        httpServletResponse.addCookie(new Cookie(name, value));

        return this;
    }

    public Response cookie(String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setMaxAge(maxAge);
        httpServletResponse.addCookie(cookie);

        return this;
    }

    public Response cookie(String path, String domain, String name, String value, int maxAge, boolean secure) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath(path);
        cookie.setDomain(domain);
        cookie.setMaxAge(maxAge);
        cookie.setSecure(secure);
        httpServletResponse.addCookie(cookie);

        return this;
    }

    public Response removeCookie(String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setMaxAge(0);
        httpServletResponse.addCookie(cookie);

        return this;
    }

    public int getStatus() {
        return httpServletResponse.getStatus();
    }

    public Response status(int status) {
        httpServletResponse.setStatus(status);

        return this;
    }

    public Response noCache() {
        header(HttpConstants.Header.PRAGMA, "No-cache");
        header(HttpConstants.Header.CACHE_CONTROL, "no-cache,no-store,max-age=0");
        getHttpServletResponse().setDateHeader("Expires", 0);

        return this;
    }

    public void write(CharSequence sequence) {
        try {
            httpServletResponse.getWriter().append(sequence);
        } catch (IOException e) {
            throw new PippoRuntimeException(e);
        }
    }

    public HttpServletResponse getHttpServletResponse() {
        return httpServletResponse;
    }

    public boolean isCommitted() {
        return httpServletResponse.isCommitted();
    }

    public void send(CharSequence content) {
        checkCommitted();

        if (getStatus() == 0) {
            status(HttpConstants.StatusCode.OK);
        }

        if (getContentType() == null) {
            header(HttpConstants.Header.CONTENT_TYPE, HttpConstants.ContentType.TEXT_HTML);
        }

        if (getCharacterEncoding() == null) {
            characterEncoding("utf-8");
        }

        write(content);

        try {
            httpServletResponse.flushBuffer();
        } catch (IOException e) {
            new PippoRuntimeException(e);
        }
    }

    public void file(File file) {
        try {
            file(file.getName(), new FileInputStream(file));
        } catch (FileNotFoundException e) {
            throw new PippoRuntimeException(e);
        }
    }

    public void file(String filename, InputStream input) {
        checkCommitted();

        if (getStatus() == 0) {
            status(HttpConstants.StatusCode.OK);
        }

//        if (isHeaderEmpty(HttpConstants.Header.CONTENT_TYPE)) {
            header(HttpConstants.Header.CONTENT_TYPE, HttpConstants.ContentType.APPLICATION_OCTET_STREAM);
//        }

        if (isHeaderEmpty(HttpConstants.Header.CONTENT_DISPOSITION)) {
            if (filename != null && !filename.isEmpty()) {
//                header(HttpConstants.Header.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"");
                header(HttpConstants.Header.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

            } else {
                header(HttpConstants.Header.CONTENT_DISPOSITION, "attachment; filename=\"\"");
            }
        }

        try {
            int length = IOUtils.copy(input, httpServletResponse.getOutputStream());
            if (isHeaderEmpty(HttpConstants.Header.CONTENT_LENGTH)) {
                contentLength(length);
            }

            httpServletResponse.flushBuffer();
        } catch (Exception e) {
            throw new PippoRuntimeException(e);
        } finally {
            IOUtils.closeQuietly(input);
        }
    }

    public void json(Object object) {
        header(HttpConstants.Header.CONTENT_TYPE, HttpConstants.ContentType.APPLICATION_JSON);
        send(new Gson().toJson(object));
    }

    public void render(String templateName) {
        render(templateName, new HashMap<String, Object>());
    }

    public void render(String templateName, Map<String, Object> model) {
        if (templateEngine == null) {
            log.error("You must set a template engine first");
            return;
        }

        // merge the model passed with the locals data
        model.putAll(getLocals());

        // render the template using the merged model
        StringWriter stringWriter = new StringWriter();
        templateEngine.render(templateName, model, stringWriter);
        send(stringWriter.toString());
    }

    /**
     * Good for storing variables for the current request/response cycle.
     * Also these variables will be available automatically to all templates for the current request/response cycle.
     *
     * @return
     */
    public Map<String, Object> getLocals() {
        if (locals == null) {
            locals = new HashMap<String, Object>();
        }

        return locals;
    }


    private boolean isHeaderEmpty(String name) {
        String value = getHttpServletResponse().getHeader(name);
        return (value == null) || value.isEmpty();
    }

    private void checkCommitted() {
        if (isCommitted()) {
            throw new PippoRuntimeException("The response has already been committed");
        }
    }

}
