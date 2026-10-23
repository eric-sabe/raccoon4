package de.onyxbits.raccoon.net;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Vector;

import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.ECPointFormat;
import org.bouncycastle.tls.NameType;
import org.bouncycastle.tls.NamedGroup;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.ServerName;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsECCUtils;
import org.bouncycastle.tls.TlsExtensionsUtils;
import org.bouncycastle.tls.TlsSession;
import org.bouncycastle.tls.TlsUtils;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.util.Arrays;

/**
 * Spoofs the SSL Handshake of an Android device (supports TLS 1.2 + Android
 * BoringSSL/Conscrypt cipher suites and extensions).
 * 
 * @author patrick
 * 
 */
class JellyBeanTlsClient extends DefaultTlsClient {

	private static final int[] SUITES = new int[] {
			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
			CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
			CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
			CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
			CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
			CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
			CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
			CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
			CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
			CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
			CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
			CipherSuite.TLS_EMPTY_RENEGOTIATION_INFO_SCSV };

	private String hostName;
	private int negotiatedCipherSuite;

	public JellyBeanTlsClient(TlsCrypto crypto) {
		this(crypto, null);
	}

	public JellyBeanTlsClient(TlsCrypto crypto, String hostName) {
		super(crypto);
		this.hostName = hostName;
	}

	@Override
	public void notifySelectedCipherSuite(int selectedCipherSuite) {
		super.notifySelectedCipherSuite(selectedCipherSuite);
		this.negotiatedCipherSuite = selectedCipherSuite;
	}

	@Override
	public TlsAuthentication getAuthentication() throws IOException {
		return new DefaultTlsAuthentication(negotiatedCipherSuite);
	}

	@Override
	protected ProtocolVersion[] getSupportedVersions() {
		return ProtocolVersion.TLSv12.downTo(ProtocolVersion.TLSv10);
	}

	@Override
	protected Vector<ServerName> getSNIServerNames() {
		if (hostName != null && !hostName.isEmpty()) {
			Vector<ServerName> sni = new Vector<ServerName>(1);
			sni.addElement(new ServerName(NameType.host_name,
					hostName.getBytes(StandardCharsets.UTF_8)));
			return sni;
		}
		return null;
	}

	@Override
	protected Vector<Integer> getSupportedGroups(Vector namedGroupRoles) {
		Vector<Integer> groups = new Vector<Integer>();
		groups.addElement(Integer.valueOf(NamedGroup.x25519));
		groups.addElement(Integer.valueOf(NamedGroup.secp256r1));
		groups.addElement(Integer.valueOf(NamedGroup.secp384r1));
		groups.addElement(Integer.valueOf(NamedGroup.secp521r1));
		return groups;
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Hashtable getClientExtensions() throws IOException {
		return super.getClientExtensions();
	}

	@Override
	protected int[] getSupportedCipherSuites() {
		return Arrays.clone(SUITES);
	}

	@Override
	public int[] getCipherSuites() {
		return Arrays.clone(SUITES);
	}

	@Override
	public void notifySessionID(byte[] sessionID) {
		super.notifySessionID(sessionID);
	}

	@Override
	public TlsSession getSessionToResume() {
		return null;
	}

}
