from django.shortcuts import render
from django.contrib.auth.forms import UserCreationForm
from django.contrib.auth import login,logout,authenticate

from django.http import HttpResponseRedirect
from django.urls import reverse

# Create your views here.
def resiter(request):
    #用户注册页面，如果是get请求，就展示空的注册表单，如果是post请求，就处理页面上用户填写的注册信息
    if request.method != "POST":
        form = UserCreationForm()
    else:
        form = UserCreationForm(data=request.POST)
        
        if form.is_valid():
            new_user = form.save()
            authenticate_user = authenticate(username=new_user.username,password=request.POST["password1"])
            login(request,authenticate_user)
            return HttpResponseRedirect(reverse("learning_logs:index"))
    context = {"form":form}
    return render(request,"users/register.html",context)


        
