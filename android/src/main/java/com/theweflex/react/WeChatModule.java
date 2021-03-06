package com.theweflex.react;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;

import androidx.annotation.Nullable;

import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.internal.Files;
import com.facebook.common.references.CloseableReference;
import com.facebook.common.util.UriUtil;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.tencent.mm.opensdk.modelbase.BaseReq;
import com.tencent.mm.opensdk.modelbase.BaseResp;
import com.tencent.mm.opensdk.modelbiz.ChooseCardFromWXCardPackage;
import com.tencent.mm.opensdk.modelmsg.SendAuth;
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX;
import com.tencent.mm.opensdk.modelmsg.ShowMessageFromWX;
import com.tencent.mm.opensdk.modelmsg.WXFileObject;
import com.tencent.mm.opensdk.modelmsg.WXImageObject;
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage;
import com.tencent.mm.opensdk.modelmsg.WXMiniProgramObject;
import com.tencent.mm.opensdk.modelmsg.WXMusicObject;
import com.tencent.mm.opensdk.modelmsg.WXTextObject;
import com.tencent.mm.opensdk.modelmsg.WXVideoObject;
import com.tencent.mm.opensdk.modelmsg.WXWebpageObject;
import com.tencent.mm.opensdk.modelpay.PayReq;
import com.tencent.mm.opensdk.modelpay.PayResp;
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;
import com.tencent.mm.opensdk.constants.ConstantsAPI;
import com.tencent.mm.opensdk.modelbiz.SubscribeMessage;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.UUID;

public class WeChatModule extends ReactContextBaseJavaModule implements IWXAPIEventHandler {
    private String appId;

    private IWXAPI api = null;
    private final static String NOT_REGISTERED = "registerApp required.";
    private final static String INVOKE_FAILED = "WeChat API invoke returns false.";
    private final static String INVALID_ARGUMENT = "invalid argument.";
    // ??????????????? kb
    private final static int THUMB_SIZE = 32;

    private static byte[] bitmapTopBytes(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        bitmap.recycle();
        return baos.toByteArray();
    }

    private static byte[] bitmapResizeGetBytes(Bitmap image, int size) {
        // little-snow-fox 2019.10.20
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // ???????????????????????????100????????????????????????????????????????????????????????? baos
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        int options = 100;
        // ????????????????????????????????? 32kb ???????????????
        while (baos.toByteArray().length / 1024 > size) {
            // ??????baos?????????baos
            baos.reset();
            if (options > 10) {
                options -= 8;
            } else {
                return bitmapResizeGetBytes(Bitmap.createScaledBitmap(image, 280, image.getHeight() / image.getWidth() * 280, true), size);
            }
            // ????????????options%?????????????????????????????????baos???
            image.compress(Bitmap.CompressFormat.JPEG, options, baos);
        }
        return baos.toByteArray();
    }

    public WeChatModule(ReactApplicationContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "RCTWeChat";
    }

    /**
     * fix Native module WeChatModule tried to override WeChatModule for module name RCTWeChat.
     * If this was your intention, return true from WeChatModule#canOverrideExistingModule() bug
     *
     * @return
     */
    public boolean canOverrideExistingModule() {
        return true;
    }

    private static ArrayList<WeChatModule> modules = new ArrayList<>();

    @Override
    public void initialize() {
        super.initialize();
        modules.add(this);
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        if (api != null) {
            api = null;
        }
        modules.remove(this);
    }

    public static void handleIntent(Intent intent) {
        for (WeChatModule mod : modules) {
            mod.api.handleIntent(intent, mod);
        }
    }

    @ReactMethod
    public void registerApp(String appid, String universalLink, Callback callback) {
        this.appId = appid;
        api = WXAPIFactory.createWXAPI(this.getReactApplicationContext().getBaseContext(), appid, true);
        callback.invoke(null, api.registerApp(appid));
    }

    @ReactMethod
    public void isWXAppInstalled(Callback callback) {
        if (api == null) {
            callback.invoke(NOT_REGISTERED);
            return;
        }
        callback.invoke(null, api.isWXAppInstalled());
    }

    @ReactMethod
    public void isWXAppSupportApi(Callback callback) {
        if (api == null) {
            callback.invoke(NOT_REGISTERED);
            return;
        }
        callback.invoke(null, api.getWXAppSupportAPI());
    }

    @ReactMethod
    public void getApiVersion(Callback callback) {
        if (api == null) {
            callback.invoke(NOT_REGISTERED);
            return;
        }
        callback.invoke(null, api.getWXAppSupportAPI());
    }

    @ReactMethod
    public void openWXApp(Callback callback) {
        if (api == null) {
            callback.invoke(NOT_REGISTERED);
            return;
        }
        callback.invoke(null, api.openWXApp());
    }

    @ReactMethod
    public void sendAuthRequest(String scope, String state, Callback callback) {
        if (api == null) {
            callback.invoke(NOT_REGISTERED);
            return;
        }
        SendAuth.Req req = new SendAuth.Req();
        req.scope = scope;
        req.state = state;
        callback.invoke(null, api.sendReq(req));
    }

    /**
     * ????????????
     *
     * @param data
     * @param callback
     */
    @ReactMethod
    public void chooseInvoice(ReadableMap data, Callback callback) {
        ChooseCardFromWXCardPackage.Req req = new ChooseCardFromWXCardPackage.Req();

        req.appId = this.appId;
        req.cardType = "INVOICE";
        req.timeStamp = String.valueOf(data.getInt("timeStamp"));
        req.nonceStr = data.getString("nonceStr");
        req.cardSign = data.getString("cardSign");
        req.signType = data.getString("signType");

        callback.invoke(null, api.sendReq(req));
    }

    public byte[] loadRawDataFromURL(String u) throws Exception {
        URL url = new URL(u);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        InputStream is = conn.getInputStream();
        BufferedInputStream bis = new BufferedInputStream(is);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final int BUFFER_SIZE = 2048;
        final int EOF = -1;

        int c;
        byte[] buf = new byte[BUFFER_SIZE];

        while (true) {
            c = bis.read(buf);
            if (c == EOF)
                break;

            baos.write(buf, 0, c);
        }

        conn.disconnect();
        is.close();

        byte[] data = baos.toByteArray();
        baos.flush();

        return data;
    }


    /**
     * ????????????
     *
     * @param data
     * @param callback
     */
    @ReactMethod
    public void shareFile(ReadableMap data, Callback callback) throws Exception {
        WXFileObject fileObj = new WXFileObject();
        fileObj.fileData = loadRawDataFromURL(data.getString("url"));

        WXMediaMessage msg = new WXMediaMessage();
        msg.mediaObject = fileObj;
        msg.title = data.getString("title");

        SendMessageToWX.Req req = new SendMessageToWX.Req();
        req.transaction = String.valueOf(System.currentTimeMillis());
        req.message = msg;
        req.scene = data.hasKey("scene") ? data.getInt("scene") : SendMessageToWX.Req.WXSceneSession;
        callback.invoke(null, api.sendReq(req));
    }

    /**
     * ????????????
     *
     * @param data
     * @param callback
     */
    @ReactMethod
    public void shareText(ReadableMap data, Callback callback) {
        //??????????????? WXTextObject ????????????????????????????????????
        WXTextObject textObj = new WXTextObject();
        textObj.text = data.getString("text");

        //??? WXTextObject ????????????????????? WXMediaMessage ??????
        WXMediaMessage msg = new WXMediaMessage();
        msg.mediaObject = textObj;
        msg.description = data.getString("text");

        SendMessageToWX.Req req = new SendMessageToWX.Req();
        req.transaction = "text";
        req.message = msg;
        req.scene = data.hasKey("scene") ? data.getInt("scene") : SendMessageToWX.Req.WXSceneSession;
        callback.invoke(null, api.sendReq(req));
    }

    /**
     * ????????????
     *
     * @param data
     * @param callback
     */
    @ReactMethod
    public void shareImage(final ReadableMap data, final Callback callback) {
        this._getImage(Uri.parse(data.getString("imageUrl")), null, new ImageCallback() {
            @Override
            public void invoke(@Nullable Bitmap bitmap) {
                Bitmap bmp = bitmap;
                int maxWidth = data.hasKey("maxWidth") ? data.getInt("maxWidth") : -1;
                if (maxWidth > 0) {
                    bmp = Bitmap.createScaledBitmap(bmp, maxWidth, bmp.getHeight() / bmp.getWidth() * maxWidth, true);
                }
                // ????????? WXImageObject ??? WXMediaMessage ??????
                WXImageObject imgObj = new WXImageObject(bmp);
                WXMediaMessage msg = new WXMediaMessage();
                msg.mediaObject = imgObj;

                // ???????????????
                msg.thumbData = bitmapResizeGetBytes(bmp, THUMB_SIZE);

                // ????????????Req
                SendMessageToWX.Req req = new SendMessageToWX.Req();
                req.transaction = "img";
                req.message = msg;
                // req.userOpenId = getOpenId();
                req.scene = data.hasKey("scene") ? data.getInt("scene") : SendMessageToWX.Req.WXSceneSession;
                callback.invoke(null, api.sendReq(req));
            }
        });

    }
    // private static final String SDCARD_ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();

    /**
     * ??????????????????
     *
     * @param data
     * @param callback
     */
    @ReactMethod
    public void shareLocalImage(final ReadableMap data, final Callback callback) {
        FileInputStream fs = null;
        try {
            String path = data.getString("imageUrl");
            if (path.indexOf("file://") > -1) {
                path = path.substring(7);
            }
//            int maxWidth = data.hasKey("maxWidth") ? data.getInt("maxWidth") : -1;
            fs = new FileInputStream(path);
            Bitmap bmp = BitmapFactory.decodeStream(fs);

//            if (maxWidth > 0) {
//                bmp = Bitmap.createScaledBitmap(bmp, maxWidth, bmp.getHeight() / bmp.getWidth() * maxWidth, true);
//            }

//            File f = Environment.getExternalStoragePublicDirectory(SDCARD_ROOT + "/react-native-wechat-lib");
//            String fileName = "wechat-share.jpg";
//            String tempPath = SDCARD_ROOT + "/react-native-wechat-lib";
//            File file = new File(f, fileName);
//            try {
//                FileOutputStream fos = new FileOutputStream(file);
//                bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
//                fos.flush();
//                fos.close();
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

//            int size = bmp.getByteCount();
//            ByteArrayOutputStream var2 = new ByteArrayOutputStream();
//            bmp.compress(Bitmap.CompressFormat.JPEG, 85, var2);
//            int size2 = var2.toByteArray().length;
            // ????????? WXImageObject ??? WXMediaMessage ??????

            WXImageObject imgObj = new WXImageObject(bmp);
            WXMediaMessage msg = new WXMediaMessage();
            msg.mediaObject = imgObj;
            // ???????????????
            msg.thumbData = bitmapResizeGetBytes(bmp, THUMB_SIZE);
            bmp.recycle();
            // ????????????Req
            SendMessageToWX.Req req = new SendMessageToWX.Req();
            req.transaction = "img";
            req.message = msg;
            // req.userOpenId = getOpenId();
            req.scene = data.hasKey("scene") ? data.getInt("scene") : SendMessageToWX.Req.WXSceneSession;
            callback.invoke(null, api.sendReq(req));
        } catch (FileNotFoundException e) {
            callback.invoke(null, false);
            e.printStackTrace();
        }
    }

    /**
     * ????????????
     *
     * @param data
     * @param callback
     */
    @ReactMethod
    public void shareMusic(final ReadableMap data, final Callback callback) {
        // ???????????????WXMusicObject?????????url
        WXMusicObject music = new WXMusicObject();
        music.musicUrl = data.hasKey("musicUrl") ? data.getString("musicUrl") : null;
        music.musicLowBandUrl = data.hasKey("musicLowBandUrl") ? data.getString("musicLowBandUrl") : null;
        music.musicDataUrl = data.hasKey("musicDataUrl") ? data.getString("musicDataUrl") : null;
        music.musicUrl = data.hasKey("musicUrl") ? data.getString("musicUrl") : null;
        music.musicLowBandDataUrl = data.hasKey("musicLowBandDataUrl") ? data.getString("musicLowBandDataUrl") : null;
        // ??? WXMusicObject ????????????????????? WXMediaMessage ??????
        final WXMediaMessage msg = new WXMediaMessage();
        msg.mediaObject = music;
        msg.title = data.hasKey("title") ? data.getString("title") : null;
        msg.description = data.hasKey("description") ? data.getString("description") : null;

        if (data.hasKey("thumbImageUrl")) {
            this._getImage(Uri.parse(data.getString("thumbImageUrl")), null, new ImageCallback() {
                @Override
                public void invoke(@Nullable Bitmap bmp) {
                    // ???????????????
                    if (bmp != null) {
                        msg.thumbData = bitmapResizeGetBytes(bmp, THUMB_SIZE);
                    }
                    // ????????????Req
                    SendMessageToWX.Req req = new SendMessageToWX.Req();
                    req.transaction = "music";
                    req.message = msg;
                    req.scene = data.hasKey("scene") ? data.getInt("scene") : SendMessageToWX.Req.WXSceneSession;
                    callback.invoke(null, api.sendReq(req));
                }
            });
        } else {
            // ????????????Req
            SendMessageToWX.Req req = new SendMessageToWX.Req();
            req.transaction = "music";
            req.message = msg;
            req.scene = data.hasKey("scene") ? data.getInt("scene") : SendMessageToWX.Req.WXSceneSession;
            callback.invoke(null, api.sendReq(req));
        }

    }

    /**
     * ????????????
     *
     * @param data
     * @param callback
     */
    @ReactMethod
    public void shareVideo(final ReadableMap data, final Callback callback) {
        // ???????????????WXVideoObject?????????url
        WXVideoObject video = new WXVideoObject();
        video.videoUrl = data.hasKey("videoUrl") ? data.getString("videoUrl") : null;
        video.videoLowBandUrl = data.hasKey("videoLowBandUrl") ? data.getString("videoLowBandUrl") : null;
        //??? WXVideoObject ????????????????????? WXMediaMessage ??????
        final WXMediaMessage msg = new WXMediaMessage(video);
        msg.title = data.hasKey("title") ? data.getString("title") : null;
        msg.description = data.hasKey("description") ? data.getString("description") : null;

        if (data.hasKey("thumbImageUrl")) {
            this._getImage(Uri.parse(data.getString("thumbImageUrl")), null, new ImageCallback() {
                @Override
                public void invoke(@Nullable Bitmap bmp) {
                    // ???????????????
                    if (bmp != null) {
                        msg.thumbData = bitmapResizeGetBytes(bmp, THUMB_SIZE);
                    }
                    // ????????????Req
                    SendMessageToWX.Req req = new SendMessageToWX.Req();
                    req.transaction = "video";
                    req.message = msg;
                    req.scene = data.hasKey("scene") ? data.getInt("scene") : SendMessageToWX.Req.WXSceneSession;
                    callback.invoke(null, api.sendReq(req));
                }
            });
        } else {
            // ????????????Req
            SendMessageToWX.Req req = new SendMessageToWX.Req();
            req.transaction = "video";
            req.message = msg;
            req.scene = data.hasKey("scene") ? data.getInt("scene") : SendMessageToWX.Req.WXSceneSession;
            callback.invoke(null, api.sendReq(req));
        }
    }

    /**
     * ????????????
     *
     * @param data
     * @param callback
     */
    @ReactMethod
    public void shareWebpage(final ReadableMap data, final Callback callback) {
        // ???????????????WXWebpageObject?????????url
        WXWebpageObject webpage = new WXWebpageObject();
        webpage.webpageUrl = data.hasKey("webpageUrl") ? data.getString("webpageUrl") : null;

        //??? WXWebpageObject ????????????????????? WXMediaMessage ??????
        final WXMediaMessage msg = new WXMediaMessage(webpage);
        msg.title = data.hasKey("title") ? data.getString("title") : null;
        msg.description = data.hasKey("description") ? data.getString("description") : null;

        if (data.hasKey("thumbImageUrl")) {
            this._getImage(Uri.parse(data.getString("thumbImageUrl")), null, new ImageCallback() {
                @Override
                public void invoke(@Nullable Bitmap bmp) {
                    // ???????????????
                    if (bmp != null) {
                        msg.thumbData = bitmapResizeGetBytes(bmp, THUMB_SIZE);
                    }
                    // ????????????Req
                    SendMessageToWX.Req req = new SendMessageToWX.Req();
                    req.transaction = "webpage";
                    req.message = msg;
                    req.scene = data.hasKey("scene") ? data.getInt("scene") : SendMessageToWX.Req.WXSceneSession;
                    callback.invoke(null, api.sendReq(req));
                }
            });
        } else {
            // ????????????Req
            SendMessageToWX.Req req = new SendMessageToWX.Req();
            req.transaction = "webpage";
            req.message = msg;
            req.scene = data.hasKey("scene") ? data.getInt("scene") : SendMessageToWX.Req.WXSceneSession;
            callback.invoke(null, api.sendReq(req));
        }
    }

    /**
     * ???????????????
     *
     * @param data
     * @param callback
     */
    @ReactMethod
    public void shareMiniProgram(final ReadableMap data, final Callback callback) {
        WXMiniProgramObject miniProgramObj = new WXMiniProgramObject();
        // ??????????????????????????????
        miniProgramObj.webpageUrl = data.hasKey("webpageUrl") ? data.getString("webpageUrl") : null;
        // ?????????:0????????????:1????????????:2
        miniProgramObj.miniprogramType = data.hasKey("miniProgramType") ? data.getInt("miniProgramType") : WXMiniProgramObject.MINIPTOGRAM_TYPE_RELEASE;
        // ???????????????id
        miniProgramObj.userName = data.hasKey("userName") ? data.getString("userName") : null;
        // ????????????????????????????????????????????????????????? query ????????????????????????????????????????????? "?foo=bar"
        miniProgramObj.path = data.hasKey("path") ? data.getString("path") : null;
        final WXMediaMessage msg = new WXMediaMessage(miniProgramObj);
        // ??????????????? title
        msg.title = data.hasKey("title") ? data.getString("title") : null;
        // ??????????????? desc
        msg.description = data.hasKey("description") ? data.getString("description") : null;

        String thumbImageUrl = data.hasKey("hdImageUrl") ? data.getString("hdImageUrl") : data.hasKey("thumbImageUrl") ? data.getString("thumbImageUrl") : null;

        if (thumbImageUrl != null && !thumbImageUrl.equals("")) {
            this._getImage(Uri.parse(thumbImageUrl), null, new ImageCallback() {
                @Override
                public void invoke(@Nullable Bitmap bmp) {
                    // ????????????????????????????????????128k
                    if (bmp != null) {
                        msg.thumbData = bitmapResizeGetBytes(bmp, 128);
                    }
                    // ????????????Req
                    SendMessageToWX.Req req = new SendMessageToWX.Req();
                    req.transaction = "miniProgram";
                    req.message = msg;
                    req.scene = data.hasKey("scene") ? data.getInt("scene") : SendMessageToWX.Req.WXSceneSession;
                    callback.invoke(null, api.sendReq(req));
                }
            });
        } else {
            // ????????????Req
            SendMessageToWX.Req req = new SendMessageToWX.Req();
            req.transaction = "miniProgram";
            req.message = msg;
            req.scene = data.hasKey("scene") ? data.getInt("scene") : SendMessageToWX.Req.WXSceneSession;
            callback.invoke(null, api.sendReq(req));
        }
    }

    @ReactMethod
    public void shareToTimeline(ReadableMap data, Callback callback) {
        if (api == null) {
            callback.invoke(NOT_REGISTERED);
            return;
        }
        _share(SendMessageToWX.Req.WXSceneTimeline, data, callback);
    }

    @ReactMethod
    public void launchMiniProgram(ReadableMap data, Callback callback) {
        if (api == null) {
            callback.invoke(NOT_REGISTERED);
            return;
        }
        WXLaunchMiniProgram.Req req = new WXLaunchMiniProgram.Req();
        // ??????????????????id
        req.userName = data.getString("userName");
        //???????????????????????????????????????????????????????????????????????????
        req.path = data.getString("path");
        // ???????????? ?????????????????????????????????
        req.miniprogramType = data.getInt("miniProgramType");
        boolean success = api.sendReq(req);
        if (!success) callback.invoke(INVALID_ARGUMENT);
    }

    /**
     * ?????????????????????
     *
     * @param data
     * @param callback
     */
    @ReactMethod
    public void subscribeMessage(ReadableMap data, Callback callback) {
        SubscribeMessage.Req req = new SubscribeMessage.Req();
        req.scene = data.hasKey("scene") ? data.getInt("scene") : SendMessageToWX.Req.WXSceneSession;
        req.templateID = data.getString("templateId");
        req.reserved = data.getString("reserved");
        callback.invoke(null, api.sendReq(req));
    }

    @ReactMethod
    public void shareToSession(ReadableMap data, Callback callback) {
        if (api == null) {
            callback.invoke(NOT_REGISTERED);
            return;
        }
        _share(SendMessageToWX.Req.WXSceneSession, data, callback);
    }

    @ReactMethod
    public void shareToFavorite(ReadableMap data, Callback callback) {
        if (api == null) {
            callback.invoke(NOT_REGISTERED);
            return;
        }
        _share(SendMessageToWX.Req.WXSceneFavorite, data, callback);
    }

    @ReactMethod
    public void pay(ReadableMap data, Callback callback) {
        PayReq payReq = new PayReq();
        if (data.hasKey("partnerId")) {
            payReq.partnerId = data.getString("partnerId");
        }
        if (data.hasKey("prepayId")) {
            payReq.prepayId = data.getString("prepayId");
        }
        if (data.hasKey("nonceStr")) {
            payReq.nonceStr = data.getString("nonceStr");
        }
        if (data.hasKey("timeStamp")) {
            payReq.timeStamp = data.getString("timeStamp");
        }
        if (data.hasKey("sign")) {
            payReq.sign = data.getString("sign");
        }
        if (data.hasKey("package")) {
            payReq.packageValue = data.getString("package");
        }
        if (data.hasKey("extData")) {
            payReq.extData = data.getString("extData");
        }
        payReq.appId = appId;
        callback.invoke(api.sendReq(payReq) ? null : INVOKE_FAILED);
    }

    private void _share(final int scene, final ReadableMap data, final Callback callback) {
        Uri uri = null;
        if (data.hasKey("thumbImage")) {
            String imageUrl = data.getString("thumbImage");

            try {
                uri = Uri.parse(imageUrl);
                // Verify scheme is set, so that relative uri (used by static resources) are not handled.
                if (uri.getScheme() == null) {
                    uri = getResourceDrawableUri(getReactApplicationContext(), imageUrl);
                }
            } catch (Exception e) {
                // ignore malformed uri, then attempt to extract resource ID.
            }
        }

        if (uri != null) {
            this._getImage(uri, new ResizeOptions(100, 100), new ImageCallback() {
                @Override
                public void invoke(@Nullable Bitmap bitmap) {
                    WeChatModule.this._share(scene, data, bitmap, callback);
                }
            });
        } else {
            this._share(scene, data, null, callback);
        }
    }

    private void _getImage(Uri uri, ResizeOptions resizeOptions, final ImageCallback imageCallback) {
        BaseBitmapDataSubscriber dataSubscriber = new BaseBitmapDataSubscriber() {
            @Override
            protected void onNewResultImpl(Bitmap bitmap) {
                if (bitmap != null) {
                    if (bitmap.getConfig() != null) {
                        bitmap = bitmap.copy(bitmap.getConfig(), true);
                        imageCallback.invoke(bitmap);
                    } else {
                        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                        imageCallback.invoke(bitmap);
                    }
                } else {
                    imageCallback.invoke(null);
                }
            }

            @Override
            protected void onFailureImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                imageCallback.invoke(null);
            }
        };

        ImageRequestBuilder builder = ImageRequestBuilder.newBuilderWithSource(uri);
        if (resizeOptions != null) {
            builder = builder.setResizeOptions(resizeOptions);
        }
        ImageRequest imageRequest = builder.build();

        ImagePipeline imagePipeline = Fresco.getImagePipeline();
        DataSource<CloseableReference<CloseableImage>> dataSource = imagePipeline.fetchDecodedImage(imageRequest, null);
        dataSource.subscribe(dataSubscriber, UiThreadImmediateExecutorService.getInstance());
    }

    private static Uri getResourceDrawableUri(Context context, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        name = name.toLowerCase().replace("-", "_");
        int resId = context.getResources().getIdentifier(
                name,
                "drawable",
                context.getPackageName());

        if (resId == 0) {
            return null;
        } else {
            return new Uri.Builder()
                    .scheme(UriUtil.LOCAL_RESOURCE_SCHEME)
                    .path(String.valueOf(resId))
                    .build();
        }
    }

    private void _share(final int scene, final ReadableMap data, final Bitmap thumbImage, final Callback callback) {
        if (!data.hasKey("type")) {
            callback.invoke(INVALID_ARGUMENT);
            return;
        }
        String type = data.getString("type");

        WXMediaMessage.IMediaObject mediaObject = null;
        if (type.equals("news")) {
            mediaObject = _jsonToWebpageMedia(data);
        } else if (type.equals("text")) {
            mediaObject = _jsonToTextMedia(data);
        } else if (type.equals("imageUrl") || type.equals("imageResource")) {
            __jsonToImageUrlMedia(data, new MediaObjectCallback() {
                @Override
                public void invoke(@Nullable WXMediaMessage.IMediaObject mediaObject) {
                    if (mediaObject == null) {
                        callback.invoke(INVALID_ARGUMENT);
                    } else {
                        WeChatModule.this._share(scene, data, thumbImage, mediaObject, callback);
                    }
                }
            });
            return;
        } else if (type.equals("imageFile")) {
            __jsonToImageFileMedia(data, new MediaObjectCallback() {
                @Override
                public void invoke(@Nullable WXMediaMessage.IMediaObject mediaObject) {
                    if (mediaObject == null) {
                        callback.invoke(INVALID_ARGUMENT);
                    } else {
                        WeChatModule.this._share(scene, data, thumbImage, mediaObject, callback);
                    }
                }
            });
            return;
        } else if (type.equals("video")) {
            mediaObject = __jsonToVideoMedia(data);
        } else if (type.equals("audio")) {
            mediaObject = __jsonToMusicMedia(data);
        } else if (type.equals("file")) {
            mediaObject = __jsonToFileMedia(data);
        }

        if (mediaObject == null) {
            callback.invoke(INVALID_ARGUMENT);
        } else {
            _share(scene, data, thumbImage, mediaObject, callback);
        }
    }

    private void _share(int scene, ReadableMap data, Bitmap thumbImage, WXMediaMessage.IMediaObject mediaObject, Callback callback) {

        WXMediaMessage message = new WXMediaMessage();
        message.mediaObject = mediaObject;

        if (thumbImage != null) {
            message.setThumbImage(thumbImage);
        }

        if (data.hasKey("title")) {
            message.title = data.getString("title");
        }
        if (data.hasKey("description")) {
            message.description = data.getString("description");
        }
        if (data.hasKey("mediaTagName")) {
            message.mediaTagName = data.getString("mediaTagName");
        }
        if (data.hasKey("messageAction")) {
            message.messageAction = data.getString("messageAction");
        }
        if (data.hasKey("messageExt")) {
            message.messageExt = data.getString("messageExt");
        }

        SendMessageToWX.Req req = new SendMessageToWX.Req();
        req.message = message;
        req.scene = scene;
        req.transaction = UUID.randomUUID().toString();
        callback.invoke(null, api.sendReq(req));
    }

    private WXTextObject _jsonToTextMedia(ReadableMap data) {
        if (!data.hasKey("description")) {
            return null;
        }

        WXTextObject ret = new WXTextObject();
        ret.text = data.getString("description");
        return ret;
    }

    private WXWebpageObject _jsonToWebpageMedia(ReadableMap data) {
        if (!data.hasKey("webpageUrl")) {
            return null;
        }

        WXWebpageObject ret = new WXWebpageObject();
        ret.webpageUrl = data.getString("webpageUrl");
        if (data.hasKey("extInfo")) {
            ret.extInfo = data.getString("extInfo");
        }
        return ret;
    }

    private void __jsonToImageMedia(String imageUrl, final MediaObjectCallback callback) {
        Uri imageUri;
        try {
            imageUri = Uri.parse(imageUrl);
            // Verify scheme is set, so that relative uri (used by static resources) are not handled.
            if (imageUri.getScheme() == null) {
                imageUri = getResourceDrawableUri(getReactApplicationContext(), imageUrl);
            }
        } catch (Exception e) {
            imageUri = null;
        }

        if (imageUri == null) {
            callback.invoke(null);
            return;
        }

        this._getImage(imageUri, null, new ImageCallback() {
            @Override
            public void invoke(@Nullable Bitmap bitmap) {
                callback.invoke(bitmap == null ? null : new WXImageObject(bitmap));
            }
        });
    }

    private void __jsonToImageUrlMedia(ReadableMap data, MediaObjectCallback callback) {
        if (!data.hasKey("imageUrl")) {
            callback.invoke(null);
            return;
        }
        String imageUrl = data.getString("imageUrl");
        __jsonToImageMedia(imageUrl, callback);
    }

    private void __jsonToImageFileMedia(ReadableMap data, MediaObjectCallback callback) {
        if (!data.hasKey("imageUrl")) {
            callback.invoke(null);
            return;
        }

        String imageUrl = data.getString("imageUrl");
        if (!imageUrl.toLowerCase().startsWith("file://")) {
            imageUrl = "file://" + imageUrl;
        }
        __jsonToImageMedia(imageUrl, callback);
    }

    private WXMusicObject __jsonToMusicMedia(ReadableMap data) {
        if (!data.hasKey("musicUrl")) {
            return null;
        }

        WXMusicObject ret = new WXMusicObject();
        ret.musicUrl = data.getString("musicUrl");
        return ret;
    }

    private WXVideoObject __jsonToVideoMedia(ReadableMap data) {
        if (!data.hasKey("videoUrl")) {
            return null;
        }

        WXVideoObject ret = new WXVideoObject();
        ret.videoUrl = data.getString("videoUrl");
        return ret;
    }

    private WXFileObject __jsonToFileMedia(ReadableMap data) {
        if (!data.hasKey("filePath")) {
            return null;
        }
        return new WXFileObject(data.getString("filePath"));
    }

    // TODO: ??????sendRequest???sendSuccessResponse???sendErrorCommonResponse???sendErrorUserCancelResponse

    @Override
    public void onReq(BaseReq baseReq) {
        WritableMap map = Arguments.createMap();
        map.putString("openId", baseReq.openId);
        map.putString("transaction", baseReq.transaction);
        if (baseReq.getType() == ConstantsAPI.COMMAND_SHOWMESSAGE_FROM_WX) {
            ShowMessageFromWX.Req req = (ShowMessageFromWX.Req) baseReq;
            // ??????JsApi navigateBackApplication??????extraData????????????
            map.putString("type", "SendMessageToWX.Resp");
            map.putString("lang", req.lang);
            map.putString("country", req.message.messageExt);
        }
        this.getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("WeChat_Resp", map);
    }

    @Override
    public void onResp(BaseResp baseResp) {
        WritableMap map = Arguments.createMap();
        map.putInt("errCode", baseResp.errCode);
        map.putString("errStr", baseResp.errStr);
        map.putString("openId", baseResp.openId);
        map.putString("transaction", baseResp.transaction);

        if (baseResp instanceof SendAuth.Resp) {
            SendAuth.Resp resp = (SendAuth.Resp) (baseResp);

            map.putString("type", "SendAuth.Resp");
            map.putString("code", resp.code);
            map.putString("state", resp.state);
            map.putString("url", resp.url);
            map.putString("lang", resp.lang);
            map.putString("country", resp.country);
        } else if (baseResp instanceof SendMessageToWX.Resp) {
            SendMessageToWX.Resp resp = (SendMessageToWX.Resp) (baseResp);
            map.putString("type", "SendMessageToWX.Resp");
        } else if (baseResp instanceof PayResp) {
            PayResp resp = (PayResp) (baseResp);
            map.putString("type", "PayReq.Resp");
            map.putString("returnKey", resp.returnKey);
        } else if (baseResp.getType() == ConstantsAPI.COMMAND_LAUNCH_WX_MINIPROGRAM) {
            WXLaunchMiniProgram.Resp resp = (WXLaunchMiniProgram.Resp) baseResp;
            // ??????JsApi navigateBackApplication??????extraData????????????
            String extraData = resp.extMsg;
            map.putString("type", "WXLaunchMiniProgramReq.Resp");
            map.putString("extraData", extraData);
            map.putString("extMsg", extraData);
        } else if (baseResp instanceof ChooseCardFromWXCardPackage.Resp) {
            ChooseCardFromWXCardPackage.Resp resp = (ChooseCardFromWXCardPackage.Resp) baseResp;
            map.putString("type", "WXChooseInvoiceResp.Resp");
            map.putString("cardItemList", resp.cardItemList);
        }

        this.getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("WeChat_Resp", map);
    }

    private interface ImageCallback {
        void invoke(@Nullable Bitmap bitmap);
    }

    private interface MediaObjectCallback {
        void invoke(@Nullable WXMediaMessage.IMediaObject mediaObject);
    }

}
