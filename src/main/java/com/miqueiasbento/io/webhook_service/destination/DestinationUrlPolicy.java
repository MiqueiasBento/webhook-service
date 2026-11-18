package com.miqueiasbento.io.webhook_service.destination;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DestinationUrlPolicy {
    private final boolean allowPrivate;

    public DestinationUrlPolicy(@Value("${webhook.security.allow-private-destinations:false}") boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    public void validate(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || !Set.of("http", "https").contains(scheme.toLowerCase(Locale.ROOT))
                    || host == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0) {
                throw invalid();
            }
            if (!allowPrivate) {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                if (addresses.length == 0) {
                    throw invalid();
                }
                for (InetAddress address : addresses) {
                    if (!isPublic(address)) {
                        throw invalid();
                    }
                }
            }
        } catch (IllegalArgumentException | UnknownHostException e) {
            throw invalid();
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or prohibited destination URL");
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int a = bytes[0] & 255;
            int b = bytes[1] & 255;
            int c = bytes[2] & 255;
            return a != 0 && a != 10 && a != 127 && a < 224
                    && !(a == 100 && b >= 64 && b <= 127)
                    && !(a == 169 && b == 254)
                    && !(a == 172 && b >= 16 && b <= 31)
                    && !(a == 192 && (b == 0 || b == 168))
                    && !(a == 198 && (b == 18 || b == 19 || b == 51 && c == 100))
                    && !(a == 203 && b == 0 && c == 113);
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 255;
            int second = bytes[1] & 255;
            return (first & 0xfe) != 0xfc
                    && !(first == 0x20 && second == 0x01 && (bytes[2] & 255) == 0x0d
                    && (bytes[3] & 255) == 0xb8);
        }
        return false;
    }
}
