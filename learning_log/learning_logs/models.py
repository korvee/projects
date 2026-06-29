from django.db import models
from django.contrib.auth.models import User

# Create your models here.
class Topic(models.Model):
    text = models.CharField(max_length=200,null=True)
    date_added = models.DateTimeField(auto_now_add=True)
    owner = models.ForeignKey(User,on_delete=models.CASCADE)

    def __str__(self):
        return self.text
    
class Entry(models.Model):
    #to-do 最终生成的entry表，表名加上了模块名即learnging_logs,然后表包含了四个字段，其中只有两个是明确定义的，主键id是自动生成的，外键topic_id，好像是根据这里设定的外键属性，后缀加了id生成的，这个mode和数据表的对应关系具体是怎样的？
    topic = models.ForeignKey(Topic,on_delete=models.CASCADE)
    text = models.TextField()
    date_added = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name_plural = "entries"
        def __str__(self):
            return self.text[:50] + "..."
