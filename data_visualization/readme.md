# python数据分析
**使用python读取csv文件、读取web api数据，然后使用使用第三方的matplotlib（直接调用操作系统的gui生成图形）和pygal（根据数据生成矢量图）生成数据图标**

## markdown常用语法
- #一级标题 ##二级标题。。。
- **加粗文本
- 1.2.有序列表 -无需列表
- （```python 增加代码快）

## 创建python隔离环境
1. 创建项目文件夹
2. 在文件夹目录执行 python -m venv .venv
3. 生效隔离环境 source /venv/bin/active
4. 可以调用pip freeze > requirements.txt将项目的所有依赖项（包括版本信息）写到requirements.txt文件中，其他人下载代码后，可以很方便的通过requirements文件复原运行环境。requirement中的依赖项的数目是要远大于pip install直接下载的包的，因为存在依赖传递，下载的包本身也有自己的依赖

**然后就可以在隔离环境下，使用pip命令下载依赖的python包了。并且此种方式可以很方便的导出所有的依赖项，方便其他人下载代码后很容易复现依赖的环境**

## python的集合类型
- list（列表）：[]，有序可变可重复
- tuple（元组）：()，有序不可变可重复
- set（集合）：{},无序可变不可重复
- dict(字典)：{k:v}，无需可变键不可重复

## web api
**很多网站都提供web api访问关键信息的功能，比如github，可通过url获取json格式的信息来做处理。比如，https://api.github.com/search/repositories?q=language:pythonsort=stars；api.github.comdi地址用来处理api调用相关的请求，后面的参数表示查找github托管的python项目**
- 下载requests包，用于http请求
- 
