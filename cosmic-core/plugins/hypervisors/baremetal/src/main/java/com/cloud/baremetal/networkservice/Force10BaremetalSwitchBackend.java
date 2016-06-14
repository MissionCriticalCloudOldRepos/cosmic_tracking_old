// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//
// Automatically generated by addcopyright.py at 01/29/2013
// Apache License, Version 2.0 (the "License"); you may not use this
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//
// Automatically generated by addcopyright.py at 04/03/2012
package com.cloud.baremetal.networkservice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.xmlobject.XmlObject;
import com.cloud.utils.xmlobject.XmlObjectParser;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Created by frank on 9/2/14.
 */
public class Force10BaremetalSwitchBackend implements BaremetalSwitchBackend {
    private Logger logger = LoggerFactory.getLogger(Force10BaremetalSwitchBackend.class);
    public static final String TYPE = "Force10";

    private static List<HttpStatus> successHttpStatusCode = new ArrayList<>();
    {
        successHttpStatusCode.add(HttpStatus.OK);
        successHttpStatusCode.add(HttpStatus.ACCEPTED);
        successHttpStatusCode.add(HttpStatus.CREATED);
        successHttpStatusCode.add(HttpStatus.NO_CONTENT);
        successHttpStatusCode.add(HttpStatus.PARTIAL_CONTENT);
        successHttpStatusCode.add(HttpStatus.RESET_CONTENT);
        successHttpStatusCode.add(HttpStatus.ALREADY_REPORTED);
    }

    RestTemplate rest = new RestTemplate();
    {
        // fake error handler, we handle error in business logic code
        rest.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse clientHttpResponse) throws IOException {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse clientHttpResponse) throws IOException {
            }
        });
    }

    private String buildLink(String switchIp, String path) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        builder.scheme("http");
        builder.host(switchIp);
        builder.port(8008);
        builder.path(path);
        return builder.build().toUriString();
    }

    @Override
    public String getSwitchBackendType() {
        return TYPE;
    }

    @Override
    public void prepareVlan(BaremetalVlanStruct struct) {
        String link = buildLink(struct.getSwitchIp(), String.format("/api/running/ftos/interface/vlan/%s", struct.getVlan()));
        HttpHeaders headers = createBasicAuthenticationHeader(struct);
        HttpEntity<String> request = new HttpEntity<>(headers);
        ResponseEntity rsp = rest.exchange(link, HttpMethod.GET, request, String.class);
        logger.debug(String.format("http get: %s", link));

        if (rsp.getStatusCode() == HttpStatus.NOT_FOUND) {
            PortInfo port = new PortInfo(struct);
            XmlObject xml = new XmlObject("vlan").putElement("vlan-id",
                    new XmlObject("vlan-id").setText(String.valueOf(struct.getVlan()))).putElement("untagged",
                    new XmlObject("untagged").putElement(port.interfaceType, new XmlObject(port.interfaceType)
                            .putElement("name", new XmlObject("name").setText(port.port)))
            ).putElement("shutdown", new XmlObject("shutdown").setText("false"));
            request = new HttpEntity<>(xml.dump(), headers);
            link = buildLink(struct.getSwitchIp(), String.format("/api/running/ftos/interface/"));
            logger.debug(String.format("http get: %s, body: %s", link, request));
            rsp = rest.exchange(link, HttpMethod.POST, request, String.class);
            if (!successHttpStatusCode.contains(rsp.getStatusCode())) {
                throw new CloudRuntimeException(String.format("unable to create vlan[%s] on force10 switch[ip:%s]. HTTP status code:%s, body dump:%s",
                        struct.getVlan(), struct.getSwitchIp(),rsp.getStatusCode(), rsp.getBody()));
            } else {
                logger.debug(String.format("successfully programmed vlan[%s] on Force10[ip:%s, port:%s]. http response[status code:%s, body:%s]",
                        struct.getVlan(), struct.getSwitchIp(), struct.getPort(), rsp.getStatusCode(), rsp.getBody()));
            }
        } else if (successHttpStatusCode.contains(rsp.getStatusCode())) {
            PortInfo port = new PortInfo(struct);
            XmlObject xml = XmlObjectParser.parseFromString((String)rsp.getBody());
            List<XmlObject> ports = xml.getAsList("untagged.tengigabitethernet");
            ports.addAll(xml.<XmlObject>getAsList("untagged.gigabitethernet"));
            ports.addAll(xml.<XmlObject>getAsList("untagged.fortyGigE"));
            for (XmlObject pxml : ports) {
                XmlObject name = pxml.get("name");
                if (port.port.equals(name.getText())) {
                    logger.debug(String.format("port[%s] has joined in vlan[%s], no need to program again", struct.getPort(), struct.getVlan()));
                    return;
                }
            }

            xml.removeElement("mtu");
            xml.setText(null);
            XmlObject tag = xml.get("untagged");
            if (tag == null) {
                tag = new XmlObject("untagged");
                xml.putElement("untagged", tag);
            }

            tag.putElement(port.interfaceType, new XmlObject(port.interfaceType)
                    .putElement("name", new XmlObject("name").setText(port.port)));
            request = new HttpEntity<>(xml.dump(), headers);
            link = buildLink(struct.getSwitchIp(), String.format("/api/running/ftos/interface/vlan/%s", struct.getVlan()));
            logger.debug(String.format("http get: %s, body: %s", link, request));
            rsp = rest.exchange(link, HttpMethod.PUT, request, String.class);
            if (!successHttpStatusCode.contains(rsp.getStatusCode())) {
                throw new CloudRuntimeException(String.format("failed to program vlan[%s] for port[%s] on force10[ip:%s]. http status:%s, body dump:%s",
                        struct.getVlan(), struct.getPort(), struct.getSwitchIp(), rsp.getStatusCode(), rsp.getBody()));
            } else {
                logger.debug(String.format("successfully join port[%s] into vlan[%s] on Force10[ip:%s]. http response[status code:%s, body:%s]",
                        struct.getPort(), struct.getVlan(), struct.getSwitchIp(), rsp.getStatusCode(), rsp.getBody()));
            }
        } else {
            throw new CloudRuntimeException(String.format("force10[ip:%s] returns unexpected error[%s] when http getting %s, body dump:%s",
                    struct.getSwitchIp(), rsp.getStatusCode(), link, rsp.getBody()));
        }
    }

    @Override
    public void removePortFromVlan(BaremetalVlanStruct struct) {
        String link = buildLink(struct.getSwitchIp(), String.format("/api/running/ftos/interface/vlan/%s", struct.getVlan()));
        HttpHeaders headers = createBasicAuthenticationHeader(struct);
        HttpEntity<String> request = new HttpEntity<>(headers);
        logger.debug(String.format("http get: %s, body: %s", link, request));
        ResponseEntity rsp = rest.exchange(link, HttpMethod.GET, request, String.class);
        if (rsp.getStatusCode() == HttpStatus.NOT_FOUND) {
            logger.debug(String.format("vlan[%s] has been deleted on force10[ip:%s], no need to remove the port[%s] anymore", struct.getVlan(), struct.getSwitchIp(), struct.getPort()));
        } else if (rsp.getStatusCode() == HttpStatus.OK) {
            PortInfo port = new PortInfo(struct);
            XmlObject xml = XmlObjectParser.parseFromString((String)rsp.getBody());
            List<XmlObject> ports = xml.getAsList("untagged.tengigabitethernet");
            ports.addAll(xml.<XmlObject>getAsList("untagged.gigabitethernet"));
            ports.addAll(xml.<XmlObject>getAsList("untagged.fortyGigE"));
            List<XmlObject> newPorts = new ArrayList<>();
            boolean needRemove = false;
            for (XmlObject pxml : ports) {
                XmlObject name = pxml.get("name");
                if (port.port.equals(name.getText())) {
                    needRemove = true;
                    continue;
                }

                newPorts.add(pxml);
            }

            if (!needRemove) {
                return;
            }

            xml.setText(null);
            xml.removeElement("mtu");
            XmlObject tagged = xml.get("untagged");
            tagged.removeAllChildren();
            for (XmlObject p : newPorts) {
                tagged.putElement(p.getTag(), p);
            }


            request = new HttpEntity<>(xml.dump(), headers);
            logger.debug(String.format("http get: %s, body: %s", link, request));
            rsp = rest.exchange(link, HttpMethod.PUT, request, String.class);
            if (!successHttpStatusCode.contains(rsp.getStatusCode())) {
                throw new CloudRuntimeException(String.format("failed to program vlan[%s] for port[%s] on force10[ip:%s]. http status:%s, body dump:%s",
                        struct.getVlan(), struct.getPort(), struct.getSwitchIp(), rsp.getStatusCode(), rsp.getBody()));
            } else {
                logger.debug(String.format("removed port[%s] from vlan[%s] on force10[ip:%s]", struct.getPort(), struct.getVlan(), struct.getSwitchIp()));
            }
        } else {
            throw new CloudRuntimeException(String.format("force10[ip:%s] returns unexpected error[%s] when http getting %s, body dump:%s",
                    struct.getSwitchIp(), rsp.getStatusCode(), link, rsp.getBody()));
        }
    }

    private HttpHeaders createBasicAuthenticationHeader(BaremetalVlanStruct struct) {
        String plainCreds = String.format("%s:%s", struct.getSwitchUsername(), struct.getSwitchPassword());
        byte[] plainCredsBytes = plainCreds.getBytes();
        byte[] base64CredsBytes = Base64.encodeBase64(plainCredsBytes);
        String base64Creds = new String(base64CredsBytes);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Basic " + base64Creds);
        headers.setAccept(Arrays.asList(MediaType.ALL));
        headers.setContentType(MediaType.valueOf("application/vnd.yang.data+xml"));
        return  headers;
    }

    private class PortInfo {
        static final String G_IFACE = "gigabitethernet";
        static final String TEN_G_IFACE = "tengigabitethernet";
        static final String FOURTY_G_IFACE = "fortyGigE";

        private String interfaceType;
        private String port;

        PortInfo(BaremetalVlanStruct struct) {
            String[] ps = StringUtils.split(struct.getPort(), ":");
            if (ps.length == 1) {
                interfaceType = TEN_G_IFACE;
                port = ps[0];
            } else if (ps.length == 2) {
                interfaceType = ps[0];
                if (!interfaceType.equals(G_IFACE) && !interfaceType.equals(TEN_G_IFACE) && !interfaceType.equals(FOURTY_G_IFACE)) {
                    throw new CloudRuntimeException(String.format("wrong port definition[%s]. The prefix must be one of [%s,%s,%s]", struct.getPort(), G_IFACE, TEN_G_IFACE, FOURTY_G_IFACE));
                }
                port = ps[1];
            } else {
                throw new CloudRuntimeException(String.format("wrong port definition[%s]. Force10 port should be in format of interface_type:port_identity, for example: tengigabitethernet:1/3", struct.getPort()));
            }
        }
    }
}
