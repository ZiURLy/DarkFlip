# DarkFlip

> An Xposed module that gives apps without a dark mode a hue-preserving inverted dark theme, while keeping photos in their original colours. Works with Flutter UIs.

给没有深色模式的 App 整体反色，而且保持色相（红的还是红，蓝的还是蓝）。照片、头像、二维码尽量保持原色。

## 功能

**反色效果**
- 白底可以变成纯黑，也可以变成深灰（对比更柔和）
- 照片、头像、彩色插图、二维码不跟着反色
- 看图、视频、扫码这类本来就是深色的页面会自动跳过
- 精确配色（Android 16 及以上）：用着色器反色，鲜艳的颜色不会发灰
- 状态栏和导航栏的图标颜色会跟着翻转
- 可以去掉卡片阴影，免得反色后在卡片周围变成一圈亮边

**适配**
- 适配 Flutter 界面，可以把 Flutter 页面改成纹理渲染，反色效果就和普通界面一致
- 适配闲鱼等 App 使用的 PowerImage 图片库

**控制**
- 跟随系统深色模式，也可以设成始终反色
- 按 Activity 类名关键词排除不想反色的页面
- 系统级模式：由系统在合成画面时反色，**完全不注入 App 进程**，适合银行类 App

## 要求

- 已 root，并装了支持 XSharedPreferences 的 Xposed 框架（LSPosed、Vector 等）
- Android 10 及以上

作者的测试环境：一加 Ace 6T，OxygenOS 16，Vector。

## 安装和使用

1. 从 [Releases](../../releases) 下载最新的 APK 并安装
2. 在 Xposed 管理器里启用本模块，作用域里勾选想变暗的 App。默认只推荐了闲鱼，其他 App 需要自己勾
3. 把目标 App 从后台划掉，重新打开
4. 打开桌面上的 DarkFlip 调整设置。**每次改完设置，都要把目标 App 从后台划掉重开才会完全生效**

**系统级模式**：在作用域里勾选「系统框架」，然后重启手机。在 DarkFlip 设置里填写包名，每行一个。名单里的 App 不需要勾进作用域。这个模式有三个代价：灰色层次差一些，图片也会被反色，而且不跟随系统深浅色（一直反色）。

## 已知问题

- App 闪退或画面花掉时，先关掉「精确配色」，模块会退回到颜色矩阵
- 系统切换深浅色后，已经加载的图片颜色可能不对，重开 App 即可
- 如果某个 App 不断重建页面，模块会自动停止对它隐藏系统深色模式，直到这个 App 重启

## 遇到问题

在设置里打开「诊断日志」，复现问题后，到 Xposed 管理器里复制带 `[DarkFlip]` 前缀的日志，然后在 [Issues](../../issues) 里附上 App 名称和版本。

想适配新的 App 时，可以打开「深度诊断」：每个 App 版本只运行一次，读取它的类名表，把和 Flutter、图片加载有关的类名写进日志。日志只保存在本机。

## 原理

- `AppEngine`：在 App 进程里给窗口加反色效果（颜色矩阵，或 Android 16 及以上的着色器）
- `Images` / `Media`：识别照片和图片，并把它们反转回原色
- `FlutterHooks`：处理 Flutter 的渲染模式
- `SystemEngine`：系统级模式，在系统框架里对指定 App 的图层做颜色变换

## 说明

原始工程丢失，本仓库的源码由发布版 APK 反编译整理而来，局部变量名是工具生成的，欢迎改进。

## 自己编译

需要 JDK 17 和 Android SDK，用 Android Studio 打开即可编译。编译 Release 包要用你自己的签名密钥，请不要把密钥文件提交到仓库。

## 许可证

[MIT](LICENSE)：可以自由使用、修改和再发布，只需保留版权声明。
