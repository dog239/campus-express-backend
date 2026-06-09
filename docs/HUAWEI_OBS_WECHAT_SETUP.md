# Huawei OBS and WeChat Warning Setup

## Current Status

This branch already supports:

- Uploading evidence images to Huawei OBS
- Reading evidence images back from a private OBS bucket through the backend
- Watermarking evidence images with timestamp and order ID
- Sending delayed-package warnings through WeChat subscribe messages
- Falling back to webhook warnings when WeChat push is not enabled

## OBS Configuration

The project now reads Huawei OBS credentials from environment variables instead of committing them into Git.

Required environment variables:

```powershell
$env:OBS_ACCESS_KEY_ID="your-ak"
$env:OBS_SECRET_ACCESS_KEY="your-sk"
```

Current application configuration:

- `obs.enabled=true`
- `obs.endpoint=obs.cn-north-4.myhuaweicloud.com`
- `obs.bucket-name=picture-64`
- `obs.folder=campus-express/evidence`

The bucket is private, so the backend keeps using `/api/evidence/view/{orderId}` to return image bytes to the frontend.

## WeChat Warning Configuration

Fill these values in `application.yml` or `application-dev.yml` before using real WeChat warning push:

```yaml
wechat:
  appid: your-wechat-appid
  secret: your-wechat-secret

warning:
  wechat:
    enabled: true
    template-id: your-subscribe-template-id
    page: pages/package/list/index
    miniprogram-state: formal
    lang: zh_CN
    title-field: thing1
    station-field: thing2
    code-field: character_string3
    date-field: time4
    remark-field: thing5
```

## Template Field Mapping

The current backend sends these values:

- `title-field`: `滞留取件预警`
- `station-field`: `驿站名 + 取件码`
- `code-field`: `取件码`
- `date-field`: `到站时间`
- `remark-field`: `超过3天未领取提醒`

If your subscribe-message template uses different keyword IDs such as `thing4` or `date2`, update the field names in `warning.wechat.*-field`.

## Important Frontend Requirement

WeChat subscribe messages will not be delivered unless the user has granted the corresponding template subscription in the mini program.

So the frontend still needs to do this:

- call `wx.requestSubscribeMessage`
- request the exact template ID used by `warning.wechat.template-id`
- store or immediately use the user's subscription authorization flow

## Local Run Example

```powershell
cd D:\campus-express-backend-remote-inspect
$env:OBS_ACCESS_KEY_ID="your-ak"
$env:OBS_SECRET_ACCESS_KEY="your-sk"
D:\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
```

## Verification Checklist

- Upload an image through `/api/evidence/upload/{orderId}`
- Confirm `photoUrl` is an OBS object key under `campus-express/evidence/...`
- View the image through `/api/evidence/view/{orderId}`
- Confirm the image contains timestamp and order ID watermark
- Adjust `package.arrival_date` to more than 3 days earlier
- Wait for the scheduled task or temporarily change the cron to test
- Confirm a warning log is inserted into `warning_log`
- Confirm WeChat subscribe-message push succeeds
