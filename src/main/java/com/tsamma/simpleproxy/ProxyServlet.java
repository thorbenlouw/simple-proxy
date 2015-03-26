package com.tsamma.simpleproxy;

import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;


public class ProxyServlet extends HttpServlet {

    private static final String HTTPS = "https";
    private static final String REMOTE_HOST = System.getProperty("simpleproxy.remote_host");
    private static final int REMOTE_PORT = Integer.valueOf(System.getProperty("simpleproxy.remote_port"));
    private static final boolean VERBOSE = System.getProperty("javax.net.debug").equals("all");


    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        doHttpMethod(HttpMethod.GET, req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doHttpMethod(HttpMethod.POST, req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doHttpMethod(HttpMethod.PUT, req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doHttpMethod(HttpMethod.DELETE, req, resp);
    }

    void doHttpMethod(HttpMethod method, final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        String oldUriString = req.getRequestURI();
        String oldPath = URI.create(oldUriString).getPath();

        String oldQueryString = req.getQueryString() != null ? "?" + req.getQueryString() : "";
        String newUri = String.format("%s://%s:%d%s%s", HTTPS, REMOTE_HOST, REMOTE_PORT, oldPath, oldQueryString);
        System.out.println(String.format("Proxying from %s to %s", oldUriString, newUri));

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new PassThroughResponseErrorHandler());

        try {
            String result = restTemplate.execute(
                    newUri, method, new CopyingRequestCallback(req), new CopyingResponseCallback(resp));
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

    }


    private static class CopyingRequestCallback implements RequestCallback {
        private final HttpServletRequest req;

        public CopyingRequestCallback(HttpServletRequest req) {
            this.req = req;
        }

        @Override
        public void doWithRequest(ClientHttpRequest clientHttpRequest) throws IOException {
            Enumeration enumeration = req.getHeaderNames();
            while (enumeration.hasMoreElements()) {
                String headerName = (String) enumeration.nextElement();
                if (headerName.equals("Host")) {
                    clientHttpRequest.getHeaders().add(headerName, REMOTE_HOST + ":" + REMOTE_PORT);
                } else {
                    clientHttpRequest.getHeaders().add(headerName, req.getHeader(headerName));
                }
            }

            for (Cookie cookie : req.getCookies()) {
                clientHttpRequest.getHeaders().add("Cookie", cookie.toString());
            }

            if (VERBOSE) {
                StringBuffer incomingRequestDetails = new StringBuffer("\n\n\n\n---------------------------------------------------\n");
                incomingRequestDetails.append("Incoming Request::::\n");
                Enumeration<String> headerNames = req.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    incomingRequestDetails.append(String.format("Header: %s ::: %s\n", headerName, req.getHeader(headerName)));
                }
                for (Cookie cookie : req.getCookies()) {
                    incomingRequestDetails.append(String.format("Cookie: %s ::: %s\n", cookie.getName(), cookie.getValue()));
                }
                incomingRequestDetails.append("body: ");
                String body = IOUtils.toString(req.getInputStream(), "UTF-8");
                incomingRequestDetails.append(body);
                incomingRequestDetails.append("\nMethod: " + req.getMethod());
                incomingRequestDetails.append("\n---------------------------------------------------------\n\n\n\n");
                System.out.println(incomingRequestDetails.toString());

                StringBuffer requestDetails = new StringBuffer("\n\n\n\n---------------------------------------------------\n");
                requestDetails.append("Outgoing Request::::\n");
                HttpHeaders headers = clientHttpRequest.getHeaders();
                for (String headerName : headers.keySet()) {
                    requestDetails.append(String.format("Header: %s ::: %s\n", headerName, headers.get(headerName)));
                }
                requestDetails.append("body: ");
                IOUtils.write(body, clientHttpRequest.getBody());
                requestDetails.append(clientHttpRequest.getBody());
                requestDetails.append("\nMethod: " + clientHttpRequest.getMethod());
                requestDetails.append("\n---------------------------------------------------------\n\n\n\n");
                System.out.println(requestDetails.toString());


            } else {
                IOUtils.copy(req.getInputStream(), clientHttpRequest.getBody());
            }

        }
    }

    private static class CopyingResponseCallback implements ResponseExtractor<String> {
        private final HttpServletResponse resp;

        public CopyingResponseCallback(HttpServletResponse resp) {
            this.resp = resp;
        }

        @Override
        public String extractData(ClientHttpResponse clientHttpResponse) throws IOException {

            HttpHeaders headers = getHttpHeadersIgnoringHttpResponseExceptions(clientHttpResponse);

            for (String key : headers.keySet()) {
                resp.addHeader(key, headers.get(key).get(0));
            }
            resp.setStatus(clientHttpResponse.getStatusCode().value());

            if (VERBOSE) {
                StringBuffer responseDetails = new StringBuffer("\n\n\n\n---------------------------------------------------\n");
                responseDetails.append(String.format("Response:::: %s %s\n", clientHttpResponse.getStatusCode().toString(),
                        clientHttpResponse.getStatusText().toString()));

                for (String key : headers.keySet()) {
                    responseDetails.append(String.format("Header: %s ::: %s\n", key, headers.get(key).get(0)));
                }

                responseDetails.append("body: ");
                String body = IOUtils.toString(clientHttpResponse.getBody(), "UTF-8");
                IOUtils.write(body, resp.getOutputStream());
                responseDetails.append(body);
                responseDetails.append("\n---------------------------------------------------------\n\n\n\n");
                System.out.println(responseDetails.toString());
            } else {
                IOUtils.copy(clientHttpResponse.getBody(), resp.getOutputStream());
            }

            return "";
        }

        private HttpHeaders getHttpHeadersIgnoringHttpResponseExceptions(ClientHttpResponse clientHttpResponse) {
            HttpHeaders headers = new HttpHeaders();
            try {
                headers = clientHttpResponse.getHeaders();
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
            return headers;
        }
    }

    private static class PassThroughResponseErrorHandler implements ResponseErrorHandler {

        @Override
        public boolean hasError(ClientHttpResponse response) throws IOException {
            return false;
        }

        @Override
        public void handleError(ClientHttpResponse response) throws IOException {
        }


    }

}
