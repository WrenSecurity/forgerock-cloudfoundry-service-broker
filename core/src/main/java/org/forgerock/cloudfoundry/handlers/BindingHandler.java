/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.cloudfoundry.handlers;

import static org.forgerock.cloudfoundry.Responses.newEmptyJsonResponse;
import static org.forgerock.cloudfoundry.Responses.newErrorJsonResponse;
import static org.forgerock.http.protocol.Status.BAD_REQUEST;
import static org.forgerock.http.protocol.Status.INTERNAL_SERVER_ERROR;
import static org.forgerock.http.protocol.Status.METHOD_NOT_ALLOWED;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.io.IOException;
import java.util.Map;

import org.forgerock.cloudfoundry.services.Service;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.json.JsonValue;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delegates the binding/unbinding operations to the requested service.
 */
public class BindingHandler implements Handler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BindingHandler.class);

    private final Map<String, Service> services;

    BindingHandler(Map<String, Service> services) {
        this.services = services;
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(Context context, Request request) {
        // /v2/service_instances/:instance_id/service_bindings/:binding_id

        Map<String, String> uriTemplateVariables = context.asContext(UriRouterContext.class).getUriTemplateVariables();
        String instanceId = uriTemplateVariables.get("instanceId");
        String bindingId = uriTemplateVariables.get("bindingId");

        switch (request.getMethod()) {
        case "PUT":
            // https://docs.cloudfoundry.org/services/api.html#binding
            LOGGER.info("Binding " + bindingId + " on service instance " + instanceId);
            try {
                JsonValue body = new JsonValue(request.getEntity().getJson());
                String serviceId = body.get("service_id").asString();
                Service service = services.get(serviceId);
                if (service == null) {
                    return newResultPromise(newErrorJsonResponse(BAD_REQUEST,
                            "Unknown service_id : " + serviceId));
                }
                return service.getBindingService().bind(instanceId, bindingId, body.get("bind_resource"),
                        body.get("parameters"));
            } catch (IOException e) {
                return newResultPromise(newErrorJsonResponse(INTERNAL_SERVER_ERROR, e));
            }
        case "DELETE":
            // https://docs.cloudfoundry.org/services/api.html#unbinding
            LOGGER.info("Unbinding " + bindingId + " on service instance " + instanceId);
            String serviceId = request.getForm().getFirst("service_id");
            Service service = services.get(serviceId);
            if (service == null) {
                return newResultPromise(newErrorJsonResponse(BAD_REQUEST,
                        "Unknown service_id : " + serviceId));
            }
            return service.getBindingService().unbind(instanceId, bindingId);
        default:
            return newResultPromise(newEmptyJsonResponse(METHOD_NOT_ALLOWED));
        }
    }
}
