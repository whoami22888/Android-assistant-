package au.com.macawrescue.fieldbook;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.StatFs;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.example.visionai.inference.LlamaModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Never sends media or text off device; INTERNET is solely for model download. */
public final class LocalVision {
    private final Activity activity;
    private final WebView view;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LlamaModel model;
    private long ptr=0;
    private final File dir;
    private static final String BASE="https://huggingface.co/ggml-org/SmolVLM2-500M-Video-Instruct-GGUF/resolve/main/";
    private static final String LM="SmolVLM2-500M-Video-Instruct-Q8_0.gguf";
    private static final String VM="mmproj-SmolVLM2-500M-Video-Instruct-Q8_0.gguf";
    private static final String LM_HASH="6f67b8036b2469fcd71728702720c6b51aebd759b78137a8120733b4d66438bc";
    private static final String VM_HASH="921dc7e259f308e5b027111fa185efcbf33db13f6e35749ddf7f5cdb60ef520b";
    public LocalVision(Activity activity, WebView view) {
        this.activity=activity;this.view=view;dir=new File(activity.getFilesDir(),"smolvlm2-models");dir.mkdirs();
    }
    private void send(String kind,String payload){
        activity.runOnUiThread(()->{
            if(view==null)return;
            String js="window.macawVisionNative&&window.macawVisionNative("+JSONObject.quote(kind)+","+JSONObject.quote(payload)+")";
            view.evaluateJavascript(js,null);
        });
    }
    @JavascriptInterface public void status(){worker.execute(()->{
        boolean ready=verified(new File(dir,LM),LM_HASH)&&verified(new File(dir,VM),VM_HASH);
        send("status",ready?"Model files verified; ready to load":"Model not installed: 546 MB download required");
    });}
    @JavascriptInterface public void installModel(){worker.execute(()->{
        try{
            StatFs fs=new StatFs(dir.getAbsolutePath());
            if(fs.getAvailableBytes()<1100L*1024*1024)throw new IllegalStateException("Free at least 1.1 GB before installing model");
            fetch(LM, LM_HASH);fetch(VM,VM_HASH);send("ready","Model downloaded and SHA-256 verified. Analysis is offline.");
        }catch(Exception e){send("error","Model installation failed: "+e.getMessage());}
    });}
    private boolean verified(File file,String sha){
        if(!file.isFile()||file.length()<10_000_000)return false;
        try{return sha256(file).equalsIgnoreCase(sha);}catch(Exception ignored){return false;}
    }
    private String sha256(File file)throws Exception{
        MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] buf=new byte[131072];
        try(InputStream in=new java.io.FileInputStream(file)){int n;while((n=in.read(buf))!=-1)d.update(buf,0,n);}
        StringBuilder b=new StringBuilder();for(byte x:d.digest())b.append(String.format(java.util.Locale.US,"%02x",x & 255));return b.toString();
    }
    private void fetch(String name,String sha)throws Exception{
        File dest=new File(dir,name); if(verified(dest,sha)){send("progress",name+" already verified");return;}
        File tmp=new File(dir,name+".part");tmp.delete();
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+name+"?download=true").openConnection();
        c.setConnectTimeout(25000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(true);
        c.connect();int code=c.getResponseCode();if(code!=200)throw new IllegalStateException("HTTP "+code+" downloading "+name);
        long expected=c.getContentLengthLong();long total=0;byte[] b=new byte[131072];int n;
        try(InputStream in=c.getInputStream();FileOutputStream output=new FileOutputStream(tmp)){
            while((n=in.read(b))!=-1){output.write(b,0,n);total+=n;
                if(total%(8*1024*1024)<131072)send("progress",name+": "+(total/(1024*1024))+" MB"+(expected>0?" / "+(expected/(1024*1024))+" MB":""));
            }
            output.getFD().sync();
        }finally{c.disconnect();}
        if(!verified(tmp,sha)){tmp.delete();throw new SecurityException("SHA-256 mismatch for "+name);}
        if(dest.exists())dest.delete(); if(!tmp.renameTo(dest))throw new IllegalStateException("Unable to finalise model download");
    }
    private void ensureLoaded(){
        if(ptr!=0)return;
        File lm=new File(dir,LM),vm=new File(dir,VM);
        if(!verified(lm,LM_HASH)||!verified(vm,VM_HASH))throw new IllegalStateException("Install both verified model files first");
        send("progress","Loading SmolVLM2 on device ...");
        if(model==null)model=new LlamaModel();
        ptr=model.loadModel(lm.getAbsolutePath(),vm.getAbsolutePath(),4,2048);
        if(ptr==0)throw new IllegalStateException("Model could not be loaded");
    }
    private static class RGB {
        byte[] bytes;int width,height;
        RGB(byte[] b,int w,int h){bytes=b;width=w;height=h;}
    }
    private RGB decode(String data)throws Exception{
        if(data==null||data.length()>3_000_000)throw new IllegalArgumentException("Frame too large (max 3 MB base64)");
        int k=data.indexOf(',');if(k>=0)data=data.substring(k+1);
        byte[] compressed=Base64.decode(data,Base64.DEFAULT);
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap bmp=BitmapFactory.decodeByteArray(compressed,0,compressed.length,opts);
        if(bmp==null)throw new IllegalArgumentException("Invalid JPG/PNG frame");
        int w=bmp.getWidth(),h=bmp.getHeight();float f=Math.min(1f,512f/Math.max(w,h));
        Bitmap scaled=Bitmap.createScaledBitmap(bmp,Math.max(1,Math.round(w*f)),Math.max(1,Math.round(h*f)),true);
        w=scaled.getWidth();h=scaled.getHeight();int[] pixels=new int[w*h];scaled.getPixels(pixels,0,w,0,0,w,h);
        byte[] rgb=new byte[w*h*3];for(int i=0;i<pixels.length;i++){int p=pixels[i];rgb[3*i]=(byte)((p>>16)&255);rgb[3*i+1]=(byte)((p>>8)&255);rgb[3*i+2]=(byte)(p&255);}
        if(scaled!=bmp)scaled.recycle();bmp.recycle();return new RGB(rgb,w,h);
    }
    /** JSON {id,frames:[base64 jpeg,...],prompt}; max 3 frames. */
    @JavascriptInterface public void analyze(String json){
        if(json==null||json.length()>7_000_000){send("error","Frames exceed size limit");return;}
        worker.execute(()->{
            String id="";try{
                JSONObject req=new JSONObject(json);id=req.optString("id","");
                JSONArray frames=req.getJSONArray("frames");if(frames.length()<1||frames.length()>3)throw new IllegalArgumentException("Select 1–3 frames");
                String prompt=req.optString("prompt", "Describe visible macaw behaviour, if present.");
                if(prompt.length()>2500)throw new IllegalArgumentException("Prompt too long");
                ensureLoaded();
                byte[][] arr=new byte[frames.length()][];int[] w=new int[frames.length()],h=new int[frames.length()];
                for(int i=0;i<frames.length();i++){RGB r=decode(frames.getString(i));arr[i]=r.bytes;w[i]=r.width;h[i]=r.height;}
                send("progress","Analysing "+frames.length()+" selected frame(s) offline ...");
                String result=frames.length()==1?model.runInference(ptr,arr[0],w[0],h[0],prompt):model.runVideoInference(ptr,arr,w,h,prompt);
                send("result",new JSONObject().put("id",id).put("text",result).toString());
            }catch(Throwable e){send("error", "Analysis failed: "+e.getClass().getSimpleName()+": "+e.getMessage());}
        });
    }
    public void shutdown(){worker.execute(()->{if(ptr!=0&&model!=null){model.freeModel(ptr);ptr=0;}});worker.shutdown();}
}