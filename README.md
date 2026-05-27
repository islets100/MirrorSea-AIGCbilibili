# MirrorSea🌊--前后端分离的微服务视频社区平台

## 实现了以下功能：
- 视频的上传、查看、点赞、评论、收藏、弹幕（有分片上传与断点续传实现）
- 用户的个人信息查看编辑、关注
- 用户个人主页权限查看修改、个人主页内容获取
- 图形码、手机号、邮箱登录注册
- 一对一实时私聊
- 文生文、文生图、智能PPT
- 关注UP动态、评论点赞私聊消息的生成与推送
- 视频和用户的聚合搜索、关键字补全、关键字高亮

## 项目亮点
- 集成讯飞星火大模型实现文生文、文生图、文生PPT，并通过存储多个账号凭证在使用时查询使用后修改凭证状态的方式突破讯飞星火QPS为2的限制同一时间可以允许数十用户同时使用大模型功能
- 使用分片上传解决上传大视频速度慢问题，使用断点续传解决因网络波动导致上传失败问题
- 使用服务端中转的架构设计实现用户一对一实时私聊
- （其实还在猛火炒制中）设计并实现面向视频创作者场景的创作辅助 Agent，结合视频文件、rag检索和本地知识库生成可靠视频标题与简介建议，提升创作者发布效率与内容包装质量
- 双JWT实现无感刷新token，降低了调用api令牌被挟持造成巨大影响的风险同时又能让用户可以自动地在后台获取新的访问令牌，无需重新登录或进行任何交互，解决了用户在访问网站就需要重新验证的问题
- SpringSecurity+JWT实现统一鉴权与授权和单点登录，解决不同服务重复鉴权问题，提升了用户体验
- XXL-JOB+Redis+RocketMQ+OpenFeign+HashSet实现自定义ElasticSearch与MySQL数据同步，增强了数据同步灵活性
- 使用Gateway+Redis+XXL-JOB+CompletableFuture+LinkedBlocking Queue实现每分钟流量低于阈值单独请求，高于阈值合并请求，并添加合并请求处理的超时机制，提升了系统的并发量与健壮性
- 技术栈丰富：使用了当下企业开发最常用的可以快速开发单体应用的SpringBoot，保证了下限的基础上引用了SpringCloud系列组件搭建微服务提高上限，并使用了流行中间件Redis、RocketMQ、ElasticSearch，按需引入技术如实时连接Websocket、转码Jave等

  ## 项目演示
https://github.com/user-attachments/assets/609fa7fc-d3d3-4a92-b0ad-73c4e037cc3b


## 技术栈
- springboot：快速开发Java应用程序
- security：具备强大的鉴权授权
- nacos：服务注册与发现
- openfeign：远程调用服务
- gateway：请求入口与路由网关
- redis：跨服务缓存
- elasticsearch：更快的查询与更高的查询匹配度
- rocketmq：异步处理队列
- minio：文件对象存储
- mybatis-plus：便捷执行单表增删改查
- mybatis-plus-join：业务层多表查询
- druid：阿里数据库连接池
- jwt：token实现形式
- swagger：接口文档
- gson：谷歌序列化转换
- hutool：集成众多工具类省去手动实现工具类
- websocket：实时长连接
- 讯飞星火api：集成大模型功能
- jave：视频操作，转码、压缩、截图、去水印等
- xxl-job：分布式可视化定时任务
- zipkin：请求链路追踪信息可视化ui
- slueth：发送请求链路追踪信息

## 后续计划
- 前端部分仍待更改 
- 聊天记录持久化存在bug，双在线持久化失败
- 加入平台辅助创作的RAG-agent

## 项目地址

项目地址：[https://labilibili.com](https://labilibili.com)，欢迎访问项目，给GitHub点个小星星就更好啦

---
