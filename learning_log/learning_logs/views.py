from django.shortcuts import render
from .models import Topic,Entry
from .forms import TopicForm,EntryForm

from django.http import HttpResponseRedirect,Http404
from django.urls import reverse

from django.contrib.auth.decorators import login_required

# Create your views here.
def index(request):
    return render(request,"learning_logs/index.html")

@login_required
def topics(request):
    #orm里配置了topic的外键：user对象的userid，django的中间件会通过 session取到访问者的user对象，挂载到request对象上
    topics = Topic.objects.filter(owner=request.user).order_by("date_added")
    context = {"topics":topics}
    return render(request,"learning_logs/topics.html",context)

@login_required
def topic(request,topic_id):
    topic = Topic.objects.get(id=topic_id)
    #这里其实继续使用topics中的查询过滤方法，也能达到非所属用户无法访问的效果，但返回内容还是有不同，一个是返回列表为空，一个是直接报错找不到资源
    #当前场景是，页面并没有链接能让非所属用户访问对应id的topic，而是用户直接输入url的访问，这种情况应该算异常操作，抛出异常是更合适的
    if topic.owner != request.user:
        raise Http404
    entries = topic.entry_set.order_by("-date_added")
    context = {"topic":topic,"entries":entries}
    return render(request,"learning_logs/topic.html",context)

@login_required
def new_topic(request):
    if request.method != "POST":
        form = TopicForm()
    else:
        form = TopicForm(request.POST)
        if form.is_valid():
            new_topic = form.save(commit=False)
            #这个就是orm框架中，面向对象思维的使用。数据库层面是对userid的比对，但代码层面转换为了对对象的比对。一句话，代码层上尽量让开发者考虑和处理对象关系而不是单一属性
            new_topic.owner = request.user
            new_topic.save()
            return HttpResponseRedirect(reverse("learning_logs:topics"))
        
    context = {"form":form}
    return render(request,"learning_logs/new_topic.html",context)

@login_required
def new_entry(request,topic_id):
    topic = Topic.objects.get(id=topic_id)

    if request.method != "POST":
        form = EntryForm()
    else:
        form = EntryForm(data=request.POST)
        if form.is_valid():
            #to-do这里用save方法生成一个新的entryform表单对象好奇怪
            new_entry = form.save(commit=False)
            new_entry.topic = topic
            new_entry.save()
            return HttpResponseRedirect(reverse("learning_logs:topic",args=[topic_id]))
    context = {"topic":topic,"form":form}
    return render(request,"learning_logs/new_entry.html",context)


def edit_entry(request,entry_id):
    entry = Entry.objects.get(id=entry_id)
    topic = entry.topic
    if topic.owner != request.user:
        raise Http404
    if request.method != "POST":
        form = EntryForm(instance=entry)
    else:
        form = EntryForm(instance=entry,data=request.POST)
        if form.is_valid():
            form.save()
            return HttpResponseRedirect(reverse("learning_logs:topic",args=[topic.id]))
    context = {"entry":entry,"topic":topic,"form":form}
    return render(request,"learning_logs/edit_entry.html",context)




