package com.gogoga.backend.util;

import com.gogoga.backend.exception.BusinessException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 微信支付加解密工具
 *
 * @author system
 * @since 2026-03-23
 */
public final class WechatPayCryptoUtil {

    private WechatPayCryptoUtil() {
    }

    /**
     * 读取商户私钥
     *
     * @param privateKeyPath 私钥路径
     * @return 私钥对象
     */
    public static PrivateKey loadPrivateKey(String privateKeyPath) {
        if (StringUtils.isBlank(privateKeyPath)) {
            throw new BusinessException(500, "微信支付私钥路径未配置");
        }

        try {
            String content = FileUtils.readFileToString(new File(privateKeyPath), StandardCharsets.UTF_8);
            String normalized = content
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(normalized);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception e) {
            throw new BusinessException(500, "读取微信支付私钥失败", e);
        }
    }

    /**
     * 读取微信平台公钥
     *
     * @param publicKeyPath 公钥路径
     * @return 公钥对象
     */
    public static PublicKey loadPublicKey(String publicKeyPath) {
        if (StringUtils.isBlank(publicKeyPath)) {
            throw new BusinessException(500, "微信支付平台公钥路径未配置");
        }

        try {
            String content = FileUtils.readFileToString(new File(publicKeyPath), StandardCharsets.UTF_8);
            String normalized = content
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(normalized);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (Exception e) {
            throw new BusinessException(500, "读取微信支付平台公钥失败", e);
        }
    }

    /**
     * RSA SHA256 签名
     *
     * @param content 待签名内容
     * @param privateKeyPath 商户私钥路径
     * @return Base64 签名值
     */
    public static String sign(String content, String privateKeyPath) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(loadPrivateKey(privateKeyPath));
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new BusinessException(500, "微信支付签名失败", e);
        }
    }

    /**
     * 验证微信回调签名
     *
     * @param content 原始内容
     * @param signatureValue 签名值
     * @param publicKeyPath 平台公钥路径
     * @return 是否通过验签
     */
    public static boolean verify(String content, String signatureValue, String publicKeyPath) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(loadPublicKey(publicKeyPath));
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureValue));
        } catch (Exception e) {
            throw new BusinessException(500, "微信支付回调验签失败", e);
        }
    }

    /**
     * 解密微信支付回调资源
     *
     * @param apiV3Key APIv3 密钥
     * @param associatedData 附加数据
     * @param nonce nonce
     * @param cipherText 密文
     * @return 明文
     */
    public static String decryptResource(String apiV3Key, String associatedData, String nonce, String cipherText) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
            if (StringUtils.isNotBlank(associatedData)) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(500, "微信支付通知解密失败", e);
        }
    }
}
