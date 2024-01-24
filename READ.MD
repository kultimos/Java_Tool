在这里我们记录一些踩坑的地方

首先是打包,目前在用Idea自带的 Spring Initializr 快速创建项目时有一个坑,默认情况在pom的configuration中有这样一个配置<skip>true</skip>,
这个配置会影响我们打包出来的jar包,导致其无法正常在window或linux环境下正常运行,这个需要注释掉;

然后就是如何在windows运行jar包,通常我们建议先在jar包所在目录,通过cmd,执行java -jar jar_name.jar来运行,如果不可以,就需要去具体定位问题,
如果可以的话,我们再去用批处理命令来运行jar包,此外我们可以在jar包所在目录创建一个application.properties,来实现配置的自定义,最后的要提供给
非开发者使用的压缩包内容应该是: ![jar包+配置文件+.bat执行文件+README](压缩包内容.png)