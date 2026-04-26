# 我的健康档案

这是一个使用 Kotlin 开发的原生 Android 应用，用于集中保存个人医疗资料。当前版本支持：

- 保存检查报告、CT、化验单、处方和备注
- 通过 Android 文件选择器添加文档或图片附件
- 记录生命体征或化验指标的数值趋势
- 为选中的指标显示简洁趋势图

## 构建

可以使用 Android Studio 打开项目，或执行：

```sh
./gradlew assembleDebug
```

应用当前使用 `SharedPreferences` 将数据保存在本机。
