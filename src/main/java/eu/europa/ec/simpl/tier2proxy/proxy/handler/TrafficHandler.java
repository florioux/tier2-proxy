package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.europa.ec.simpl.tier2proxy.authprovider.AuthProviderClient;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.TLS;
import eu.europa.ec.simpl.tier2proxy.proxy.http.HTTPClient;
import eu.europa.ec.simpl.util.PemConverter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ReferenceCountUtil;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class TrafficHandler extends SimpleChannelInboundHandler<ByteBuf> {
    public static final int EXPECTED_FIRST_TLS_BYTE = 0x16;
    public static final int EXPECTED_SECOND_TLS_BYTE = 0x03;

    private final Certificates certificates;
    private final Addr dest;
    private final int httpObjectAggregatorMaxContentLength;
    private final BootstrapFactory bootstrapFactory;

    public TrafficHandler(
            Certificates certificates,
            Addr dest,
            int httpObjectAggregatorMaxContentLength,
            BootstrapFactory bootstrapFactory) {
        this.certificates = certificates;
        this.dest = dest;
        this.httpObjectAggregatorMaxContentLength = httpObjectAggregatorMaxContentLength;
        this.bootstrapFactory = Objects.requireNonNullElseGet(bootstrapFactory, BootstrapFactory::new);
    }

    public TrafficHandler(Certificates certificates, Addr dest, int httpObjectAggregatorMaxContentLength) {
        this(certificates, dest, httpObjectAggregatorMaxContentLength, null);
    }

    private static boolean isTLS(ByteBuf msg) {
        var i = new AtomicInteger();
        var firstByte = msg.getUnsignedByte(i.getAndIncrement());
        var secondByte = msg.getUnsignedByte(i.get());

        return firstByte == EXPECTED_FIRST_TLS_BYTE && secondByte == EXPECTED_SECOND_TLS_BYTE;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        log.info("inbound for {}: {}", ctx.name(), msg);
        var messageCopy = msg.copy();

        if (isTLS(messageCopy)) {
            var sni = TLS.extractSNI(messageCopy);

            if (sni.isEmpty()) {
                throw new IllegalStateException("SNI must be present");
            }

            var newDest = new Addr(sni.get(), this.dest.port());

            var certificateInfo = certificates.certificateFor(dest.addr());

            ctx.pipeline()
                    .replace(
                            this,
                            MitmHandler.class.getCanonicalName(),
                            new MitmHandler(
                                    newDest,
                                    this.httpObjectAggregatorMaxContentLength,
                                    new AuthProviderClient(
                                            new HTTPClient(bootstrapFactory.get(ctx)),
                                            new ObjectMapper().registerModule(new JavaTimeModule()),
                                            new PemConverter()),
                                    TLS.getServerSslContext(
                                            certificateInfo.privateKey(), certificateInfo.certificate())))
                    .fireChannelRead(ReferenceCountUtil.retain(msg));
        } else {
            var em = new EmbeddedChannel();

            em.pipeline()
                    .addFirst(
                            new HttpServerCodec(), new HttpObjectAggregator(this.httpObjectAggregatorMaxContentLength));

            em.writeInbound(ReferenceCountUtil.retain(msg));

            var request = em.readInbound();

            ctx.pipeline()
                    .replace(
                            this,
                            MitmHandler.class.getCanonicalName(),
                            new MitmHandler(
                                    this.dest,
                                    this.httpObjectAggregatorMaxContentLength,
                                    new AuthProviderClient(
                                            new HTTPClient(bootstrapFactory.get(ctx)),
                                            new ObjectMapper().registerModule(new JavaTimeModule()),
                                            new PemConverter()),
                                    null))
                    .fireChannelRead(ReferenceCountUtil.retain(request));
        }
    }
}
