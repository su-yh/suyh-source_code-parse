protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
    HttpServletRequest processedRequest = request;
    HandlerExecutionChain mappedHandler = null;
    boolean multipartRequestParsed = false;

    WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);

    try {
        ModelAndView mv = null;
        Exception dispatchException = null;

        try {
            // 检查当前请求是否为文件上传请求，对于文件上传请求mvc 的版本会有一些简单的特殊处理。
            processedRequest = checkMultipart(request);
            // 如果是文件处理请求，则做一个标记，后续会有清理工作。
            multipartRequestParsed = (processedRequest != request);

            // Determine handler for the current request.
            // 找到一个相应的执行器链
            // 对于controller 的实现方式，就是通过RequestMappingHandlerMapping 找到对应的RequestMapping 标记的方法。
            // 同时还会将相应的拦截器添加到该执行器链中，这些拦截器会被缓存起来。
            mappedHandler = getHandler(processedRequest);
            if (mappedHandler == null) {
                // 404：没有找到与之匹配的路径
                noHandlerFound(processedRequest, response);
                return;
            }

            // Determine handler adapter for the current request.
            // 获取一个匹配的适配器
            // 对于controller 方式的实现，这里将会得到一个RequestMappingHandlerAdapter
            // 对于其他的几个HandlerAdapter，基本都是被淘汰的处理方式，我们现在基本也不用它。
            // 使用起来不好用，很多人都没怎么听说过。
            HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

            // Process last-modified header, if supported by the handler.
            String method = request.getMethod();
            boolean isGet = HttpMethod.GET.matches(method);
            if (isGet || HttpMethod.HEAD.matches(method)) {
                long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
                if (new ServletWebRequest(request, response).checkNotModified(lastModified) && isGet) {
                    return;
                }
            }

            // 拦截器的前置处理
            if (!mappedHandler.applyPreHandle(processedRequest, response)) {
                return;
            }

            // Actually invoke the handler.
            // 具体的调用逻辑，所有的核心实现都在这里，包括参数解析，请求体解析，方法调用。
            // 返回值处理，返回值解析。
            // RequestBodyAdvice, ResponseBodyAdvice, HttpMessageConverter 等等...
            mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

            if (asyncManager.isConcurrentHandlingStarted()) {
                return;
            }

            applyDefaultViewName(processedRequest, mv);
            
            // 拦截器的后置处理
            mappedHandler.applyPostHandle(processedRequest, response, mv);
        }
        catch (Exception ex) {
            dispatchException = ex;
        }
        catch (Throwable err) {
            // As of 4.3, we're processing Errors thrown from handler methods as well,
            // making them available for @ExceptionHandler methods and other scenarios.
            dispatchException = new NestedServletException("Handler dispatch failed", err);
        }
        
        // 这里还有一个关键处理，返回值的后置处理。
        // 异常处理也就是在这里处理的，包括 ControllerAdvice, HandlerExceptionResolver
        // 拦截器的afterCompletion 调用也是在这里面执行的
        processDispatchResult(processedRequest, response, mappedHandler, mv, dispatchException);
    }
    catch (Exception ex) {
        triggerAfterCompletion(processedRequest, response, mappedHandler, ex);
    }
    catch (Throwable err) {
        triggerAfterCompletion(processedRequest, response, mappedHandler,
                new NestedServletException("Handler processing failed", err));
    }
    finally {
        if (asyncManager.isConcurrentHandlingStarted()) {
            // Instead of postHandle and afterCompletion
            if (mappedHandler != null) {
                mappedHandler.applyAfterConcurrentHandlingStarted(processedRequest, response);
            }
        }
        else {
            // Clean up any resources used by a multipart request.
            if (multipartRequestParsed) {
                cleanupMultipart(processedRequest);
            }
        }
    }
}
