# AR少儿百科-热更新方案

## 1.变化

### 1.1 资源打包方式

热更新支持的资源包格式：拿书本天文(tianwen)为例,他包含的目录: content1, content2. 

当每个目录UI展示效果做完之后, 我们生成assetbundle时,生成assetbundle包的代码为:

```c#
// 以content1目录UI效果为例
string basePath = "c:/assetbundles";
string resPath = basePath + "/tianwen/content1";
BuildPipeline.BuildAssetBundles(resPath, BuildAssetBundleOptions.None, target);
```

执行上述打包语句之后, 将在**c:/assetbundles/tianwen/content1**目录中生成一个content1文件, 一个content1.manifest文件,还包含其他的content1目录效果的UI资源的assetbundle文件.

​     之所以要这样打包的原因: 因为加载bundle资源的时候,是通过c:/assetbundles/tianwen/content1/content1这个文件来将必须的资源依赖加入到内存中. **同时, 这里也有一个问题需要注意的是app端解压下载到的zip包文件, 将数据存入本地数据库时必须要存对应的目录名字到数据库中,而不能紧紧只存储id号之类的数据.**

### 1.2 lua脚本的编写

​    针对热更新的lua脚本, 建议在Scripts目录中创建lua目录,在lua目录中, 针对每本书的内容有一个单独的目录用于存储lua脚本.lua目录的结构如下:

​    --Scripts/lua

​       --tianwen

​          --content1

​             --Logic

​             --Controller

​             --View

​     书本的每个目录有一个单独的lua脚本目录, 这样做得目录是脚本结构清晰, 便于管理, 同时也便于lua脚本的加载.

### 1.3 上传的书本目录结构的变化

 ![1](images\1.png)

这个书本的目录结构也就是要打包成zip文件从后台上传的最终的一个目录结构.

## 2. 热更新详细步骤

### 2.1  资源和脚本下载

​    资源和脚本的下载这里不发生变化, 依旧使用之前下载书本资源的那个接口. 也就是说资源和脚本这里是一次性从服务器download下来, 而在扫描的时候只需要加载相应的资源和脚本到内存即可.(**这里的话就要看要不要做细, 如果做细就是针对每个书本内容时加载; 这样的话就得要考虑资源和脚本的download方式了**)

### 2.2 书本内容界面

   **左右滑动操作**

   在书本内容界面, 我们通过左右滑动或者方向按钮来触发浏览不同的内容时,在每次触发的过程中我们必须要记住当前的书本和当前的内容名(在代码中通过变量来记住), 记住这两个值的目的是: **在后续加载资源场景时知道要加载哪个目录下的资源和哪个目录的lua脚本文件**.

​    **点击扫描操作**

​    在点击扫描(点击眼睛的图标)之后, 进入了一个新的场景(假设取名为: AR).那么我们在AR这个场景上就是实现资源加载和lua脚本加载的场景. 实现方式:  在AR场景上, 挂载simpleframework框架(**修改后的**)中的GlobalGenerator.cs脚本就可以了.

​     **退出AR场景**

​    退出AR场景时, 必须要销毁AR场景, 这样绑定在他上面的GlobalGenerator.cs等等资源才会被销毁. 这样做得目的是防止资源没有被销毁对后续的AR场景的影响.

### 2.3 simpleframework框架的修改记录

这里只列出修改后的代码记录片段:

以下代码中的**CurrentOperator**脚本是我自己添加的脚本类, 这个类的目的是记录当前选中的书本和书本内容的值



**Scripts/Manager/ResourceManager.cs**中的*LoadDependencies*

```c#
/// <summary>
/// 载入依赖
/// </summary>
/// <param name="name"></param>
void LoadDependencies(string name) {
  if (manifest == null) {
    // 改成这样的目的是防止加载资源时, 由于manifest的原因载不到内容
    string uri = string.Empty;
    uri = Util.DataPath + CurrentOperator.CurrentDir + "/" + CurrentOperator.CurrentBookContent;
    byte[] stream = File.ReadAllBytes(uri);
    assetbundle = AssetBundle.LoadFromMemory(stream);
    manifest = assetbundle.LoadAsset<AssetBundleManifest>("AssetBundleManifest");
    //Debug.LogError("Please initialize AssetBundleManifest by calling AssetBundleManager.Initialize()");
    //return;
  }
  // Get dependecies from the AssetBundleManifest object..
  string[] dependencies = manifest.GetAllDependencies(name);
  if (dependencies.Length == 0) return;

  for (int i = 0; i < dependencies.Length; i++)
    dependencies[i] = RemapVariantName(dependencies[i]);

  // Record and load all dependencies.
  for (int i = 0; i < dependencies.Length; i++) {
    LoadAssetBundle(dependencies[i]);
  }
}

/// <summary>
/// 载入AssetBundle
/// </summary>
/// <param name="abname"></param>
/// <returns></returns>
AssetBundle LoadAssetBundle(string abname) {
  if (!abname.EndsWith(AppConst.ExtName)) {
    abname += AppConst.ExtName;
  }
  AssetBundle bundle = null;
  if (!bundles.ContainsKey(abname)) {
    byte[] stream = null;
    string uri = Util.DataPath + CurrentOperator.CurrentDir + "/" + abname;  //XIEYU修改, 指定到当前目录下
    Debug.LogWarning("LoadFile::>> " + uri);
    LoadDependencies(abname);

    stream = File.ReadAllBytes(uri);
    bundle = AssetBundle.LoadFromMemory(stream); //关联数据的素材绑定
    bundles.Add(abname, bundle);
  } else {
    bundles.TryGetValue(abname, out bundle);
  }
  return bundle;
}
```

**Scripts/Manager/GameManager.cs**

```c#
/// <summary>
/// 初始化
/// </summary>
void Init() {
  DontDestroyOnLoad(gameObject);  //防止销毁自己

  // CheckExtractResource(); //释放资源
  StartCoroutine(OnExtractLuaResource());  // XIEYU修改, 原因: 加载资源和脚本不再在这个脚本里面实现
  Screen.sleepTimeout = SleepTimeout.NeverSleep;
  Application.targetFrameRate = AppConst.GameFrameRate;
}
```

 

```c#
/// <summary>
/// 处理lua脚本, 将lua脚本复制到Util.DataPath + "lua/"目录下
/// 作者:XIEYU
/// </summary>
/// <returns></returns>
IEnumerator OnExtractLuaResource()
{
  string dataPath = Util.DataPath + "lua/";  // 存放所有lua脚本的目录, lua脚本的加载路径就是基于这个目录
  string systemLuaResourcePath = Util.AppSystemLuaPath();  // 系统级的lua脚本的目录(这个是在打包apk时生成的)

  string customLuaResourcePath = Util.DataPath + CurrentOperator.CurrentLuaDir;  // 自己实现的当前操作目录中的lua脚本

  if (!Directory.Exists(dataPath))
  {
    Directory.CreateDirectory(dataPath);
  }

  List<string> customLuaFiles = Recursive(customLuaResourcePath);
  List<string> systemLuaFiles = Recursive(systemLuaResourcePath);

  foreach (string customLuaFile in customLuaFiles)
  {
    string outPath = dataPath + customLuaFile.Replace(Util.DataPath, string.Empty);
    string dirPath = Path.GetDirectoryName(outPath);

    if (!Directory.Exists(dirPath))
    {
      Directory.CreateDirectory(dirPath);
    }

    if (Application.platform == RuntimePlatform.Android)
    {
      WWW www = new WWW(customLuaFile);
      yield return www;

      if (www.isDone)
      {
        File.WriteAllBytes(outPath, www.bytes);
      }
      yield return 0;
    }
    else
    {
      File.Copy(customLuaFile, outPath, true);
    }

  }

  foreach (string systemLuaFile in systemLuaFiles)
  {
    string outPath = dataPath + systemLuaFile.Replace(systemLuaResourcePath, string.Empty);
    string dirPath = Path.GetDirectoryName(outPath);

    if (!Directory.Exists(dirPath))
    {
      Directory.CreateDirectory(dirPath);
    }

    if (Application.platform == RuntimePlatform.Android)
    {
      WWW www = new WWW(systemLuaFile);
      yield return www;

      if (www.isDone)
      {
        File.WriteAllBytes(outPath, www.bytes);
      }
      yield return 0;
    }
    else
    {
      File.Copy(systemLuaFile, outPath, true);
    }
  }

  OnResourceInited();
}

/// <summary>
/// 检索出lua脚本文件
/// 作者: XIEYU
/// </summary>
/// <param name="path"></param>
/// <returns></returns>
public List<string> Recursive(string path)
{
  List<string> files = new List<string>();
  string[] names = Directory.GetFiles(path);
  string[] dirs = Directory.GetDirectories(path);
  foreach (string filename in names)
  {
    string ext = Path.GetExtension(filename);
    if (!ext.Equals(".lua"))
      continue;
    files.Add(filename.Replace('\\', '/'));
  }
  foreach (string dir in dirs)
  {
    files.AddRange(Recursive(dir));
  }

  return files;
}

/// <summary>
/// 资源初始化结束
/// </summary>
public void OnResourceInited() {
  LuaManager.Start();
  //LuaManager.DoFile("Logic/Network");      //XIEYU修改, 不需要这个网络模块
  LuaManager.DoFile(CurrentOperator.CurrentLuaDir + "/Logic/GameManager");   //XIEYU修改, 加载当前指定目录下的GameManger.lua脚本
  initialize = true;  

  //NetManager.OnInit();    //XIEYU修改

  object[] panels = CallMethod("LuaScriptPanel");
  //---------------------Lua面板---------------------------
  foreach (object o in panels) {
    string name = o.ToString().Trim();
    if (string.IsNullOrEmpty(name)) continue;
    name += "Panel";    //添加

    LuaManager.DoFile(CurrentOperator.CurrentLuaDir + "/View/" + name);  //XIEYU修改,加载当前指定目录下的View脚本文件
    Debug.LogWarning("LoadLua---->>>>" + name + ".lua");
  }
  //------------------------------------------------------------
  CallMethod("OnInitOK");   //初始化完成
}

/// <summary>
/// 析构函数
/// </summary>
void OnDestroy() {
  // XIEYU修改, 不需要网络模块
  //if (NetManager != null) {
  //    NetManager.Unload();
  //}
  if (LuaManager != null) {
    LuaManager.Destroy();
    LuaManager = null;
  }
  Debug.Log("~GameManager was destroyed");
}
```

**Scripts/Utility/Util.cs**

```c#
/// <summary>
/// 作用: 返回系统级的lua脚本的存放路径
/// 作者: XIEYU
/// </summary>
/// <returns></returns>
public static string AppSystemLuaPath()
{
  string path = string.Empty;
  switch (Application.platform)
  {
    case RuntimePlatform.Android:
      path = "jar:file://" + Application.dataPath + "!/assets/lua/";
      break;
    case RuntimePlatform.IPhonePlayer:
      path = Application.dataPath + "/Raw/lua/";
      break;
    default:
      path = Application.dataPath + "/" + AppConst.AssetDirname + "/lua";
      break;
  }
  return path;
}
```

**Scripts/Framework/Core/View.cs**

```c#
//protected NetworkManager NetManager {
//    get {
//        if (m_NetMgr == null) {
//            m_NetMgr = facade.GetManager<NetworkManager>(ManagerName.Network);
//        }
//        return m_NetMgr;
//    }
//}
```

### 2.4 lua脚本编写修改记录

在编写lua脚本时, 用require命令导入相应的模块时,路径的变化改变

```lua
require "book1/content1/lua/Controller/BottomCtrl"
require "book1/content1/lua/Controller/SettingsCtrl"
require "book1/content1/lua/Controller/DialogCtrl"
require "book1/content1/lua/Controller/PeopleCtrl"
```

## 3. 待确认项

   **还有一点需要验证的是: 因为我们做的验证demo是简单的基于unity原生的组件;所以之前编写的UI效果(内嵌模型资源)的控制逻辑代码, 修改为lua脚本之后是否能达到相同的效果, 是否能够满足要求. 这个需要确认.**