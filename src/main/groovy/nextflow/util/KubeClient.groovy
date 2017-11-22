/*
 * Copyright (c) 2013-2017, Centre for Genomic Regulation (CRG).
 * Copyright (c) 2013-2017, Paolo Di Tommaso and the respective authors.
 *
 *   This file is part of 'Nextflow'.
 *
 *   Nextflow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nextflow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nextflow.  If not, see <http://www.gnu.org/licenses/>.
 */

package nextflow.util
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate

import groovy.json.JsonOutput

/**
 * Kubernetes API client
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class KubeClient {

    def dummyCerts = [
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null }
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            }

    ] as TrustManager[]

    String host = "192.168.99.100:8443"

    String namespace = 'default'

    String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tM3F6bXgiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjBiMGZlNWVjLWE2MTktMTFlNy1iZDk5LTA4MDAyNzc2ODYyMSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.mYe0aNtZOABvutaTdGy-MmBQ2kNXYJn-qiLXct5z9IjV2KVTk8BRp3ePRnm-0mXhDCEER0rDLnt52ccyUJn1xQpcpBxykxlDVdDTVOGH4eVARbASmB7mgKiNnkwplFpQnl7kOPsRvF4yg25wGmR6NeI7NHi4W7uatEa3mCtOFLe1KYhIsqKbhVXwfUtG-Ju0LA3yM0QuZYbHIpjBrd0KRzz4AIjWha5OKRQqk1kDd7YA83QwCpUpCs1myCQjXhgwt06FxX0rcpgVom4tB9bJDMt0i3ExrhcVZV6F3I8zYouEy1AFkdcYOCjX0a-EaVlP2RI4RO_0_JqPeG5sJnnunw"

    KubeClient() {
        trustAllCerts()
    }

    void trustAllCerts() {
        try {
            //System.setProperty("javax.net.debug", "all");
            System.setProperty("https.protocols", "TLSv1.2");

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, dummyCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
        }
        catch (Exception e) {
        }

    }

    def podsCreate(String spec) {
        postUrl("https://$host/api/v1/namespaces/$namespace/pods", spec)
    }

    String podsList(boolean allNamespaces=false) {
        def action = allNamespaces ? "pods" : "namespaces/$namespace/pods"
        getUrl("https://$host/api/v1/$action")
    }

    String podsStatus(String name) {
        getUrl("https://$host/api/v1/namespaces/$namespace/pods/$name/status")
    }

    String podsLog(String name) {
        getUrl("https://$host/api/v1/namespaces/$namespace/pods/$name/log")
    }

    protected postUrl(String path, String spec) {
        makeRequest(path,spec)
    }

    protected makeRequest(String path, String body=null, String method=null) {
        def conn = new URL(path).openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")

        if( !method ) method = body ? 'POST' : 'GET'
        conn.setRequestMethod(method)

        if( body ) {
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.getOutputStream() << body
            conn.getOutputStream().flush()
        }

        def code = conn.getResponseCode()
        def resp = code >= 400 ? conn.errorStream.text : conn.getInputStream().text

        return [code, resp]
    }

    protected getUrl(String path) {
        makeRequest(path)
    }

    String api() {
        getUrl("https://$host/api")
    }


    static int main(String[] args) {

        def spec = [
                apiVersion: 'v1',
                kind: 'Pod',
                metadata: [name: 'hello-9', namespace: 'default', labels: [app: "nextflow"]],
                spec: [
                        restartPolicy: 'Never',
                        containers: [
                                [
                                        name: 'foo',
                                        image: 'debian',
                                        command: ['bash', '-c', 'env | sort' ]
                                ]
                        ]
                ]
        ]

        def client = new KubeClient()
        def result = client.podsCreate(JsonOutput.toJson(spec))
        println result
        0
    }
}

