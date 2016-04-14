package org.infinispan.server.hotrod.logging;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import org.infinispan.server.hotrod.CacheDecodeContext;
import org.infinispan.server.hotrod.ErrorResponse;
import org.infinispan.server.hotrod.NewHotRodOperation;
import org.infinispan.server.hotrod.OperationResponse;
import org.infinispan.server.hotrod.Response;

import java.util.concurrent.Callable;

/**
 * Very simple handler that sole purpose is to put the decode context into the channel
 *
 * @author wburns
 * @since 9.0
 */
@ChannelHandler.Sharable
public class LoggingContextHandler extends ChannelDuplexHandler {
   public LoggingContextHandler() { }

   private static final LoggingContextHandler INSTANCE = new LoggingContextHandler();

   public static final LoggingContextHandler getInstance() {
      return INSTANCE;
   }



   public static final AttributeKey<CacheDecodeContext> DECODE_CONTEXT_KEY = AttributeKey.newInstance("__decodeContextKey");
   public static final AttributeKey<NewHotRodOperation> OPERATION_KEY = AttributeKey.newInstance("__operationKey");
   public static final AttributeKey<String> CACHE_NAME_KEY = AttributeKey.newInstance("__cacheNameKey");
   public static final AttributeKey<String> EXCEPTION_MESSAGE_KEY = AttributeKey.newInstance("__exceptionMessageKey");

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof CacheDecodeContext) {
         ctx.channel().attr(DECODE_CONTEXT_KEY).set((CacheDecodeContext) msg);
         ctx.channel().attr(EXCEPTION_MESSAGE_KEY).remove();
      }
      super.channelRead(ctx, msg);
   }

   @Override
   public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
      if (msg instanceof ErrorResponse) {
         ErrorResponse errorResponse = (ErrorResponse) msg;
         ctx.channel().attr(OPERATION_KEY).set(OperationResponse.fromResponse(errorResponse.operation()));
         ctx.channel().attr(CACHE_NAME_KEY).set(errorResponse.cacheName());
         ctx.channel().attr(EXCEPTION_MESSAGE_KEY).set(errorResponse.msg());
      }
      super.write(ctx, msg, promise);
   }
}
