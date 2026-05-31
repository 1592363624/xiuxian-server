package com.mtxgdn.rest;

import com.mtxgdn.util.GameLogger;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    private static final GameLogger LOG = GameLogger.getLogger(GenericExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        LOG.error("REST未处理异常", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"code\":500,\"message\":\"服务器内部错误，请稍后再试\"}")
                .build();
    }
}
