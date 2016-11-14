## SimpleFramework解读

SimpleFramework框架基于PureMVC,利用ulua来实现热更新。

PureMVC框架的整体架构师相对简单的:

主要结构就是三个文件夹目录: 

* Interfaces(框架的所有接口)
* Core(MVC的核心类: Model, Controller, View): 分别实现Interfaces中的三个接口类IModel, IController, IView;
* Patterns(其他的实现类): 也是实现了Interfaces中的接口, 主要用于控制实现加载PureMVC的整个加载过程.

1. 整个SimpleFramework框架的入口类：GlobalGenerator.cs

2. GlobalGenerator.cs类通过InitGameMangager()方法来实现所有必要资源的加载.

    a. 主要步骤在于AppFacade类中, 这个类继承自PureMVC框架中的Facade类(实现了IFacade接口).AppFacade类实现了通过Notifier的方式用于控制整个框架的启动.他会管理框架里面的Model, Controller和View三个类的一个实例. 

     第一步:  初始化AppFacade类的过程,  执行重写Facade中的virtual方法InitializeController()注册了一个NotiConst.START_UP和Command类型为StartUpCommand类型的Command到Facade中的Controller实例上面(在Controller的commandMap中加入一个映射关系), Controller会在他控制的View实例上添加一个对应类型的Observer实例.

   第二步:  发送一个类型为NotiConst.START_UP的通知, 开始启动框架.在启动框架的过程中, 根据NotiConst.START_UP的类型会执行相应在第一步中注册到View上的对应的Observer(通过反射的方式).在Observer.NotifyObserver方法中.

   ```c#
   // Observer.cs
   public virtual void NotifyObserver(INotification notification)
   {
     object context;
     string method;
     lock (m_syncRoot)
     {
       context = NotifyContext;
       method = NotifyMethod;  // "executeCommand"
     }

     Type t = context.GetType();
     BindingFlags f = BindingFlags.Instance | BindingFlags.Public | BindingFlags.IgnoreCase;
     MethodInfo mi = t.GetMethod(method, f);
     mi.Invoke(context, new object[] { notification });  // 这里会条用Controoler中的ExecuteCommand方法
   }
   ```

```c#
// Controller.cs
public virtual void ExecuteCommand(INotification note)
{
  Type commandType = null;

  lock (m_syncRoot)
  {
    if (!m_commandMap.ContainsKey(note.Name)) return;
    commandType = m_commandMap[note.Name];
  }

  object commandInstance = Activator.CreateInstance(commandType);

  if (commandInstance is ICommand)
  {
    ((ICommand) commandInstance).Execute(note);   //这里会执行到StartUpCommand中的excute方法
  }
}
```

```c#
// StartUpCommand方法, 查看此方法就会发现在MacroCommand中的构造函数中会调用InitializeMacroCommand()方法
public class StartUpCommand : MacroCommand {

    protected override void InitializeMacroCommand() {
        base.InitializeMacroCommand();

        if (!Util.CheckEnvironment()) return;

        //BootstrapModels
        AddSubCommand(typeof(BootstrapModels));

        //BootstrapCommands 
      // 这个类的Execute方法中会将各种管理器挂到Facede的Instance上面, 他的Execute方法在父类MacroCommand的Execute方法中会被调用.这里也就是各个管理器(PanelManager, GameManager, MusicManager, ResourceManager等)被挂在GameManager对象上的原因, GameManager被最后加载.
        AddSubCommand(typeof(BootstrapCommands));  //

        //BootstrapViewMediators
        AddSubCommand(typeof(BootstrapViewMediators));
    }

}

// BootstrapCommands.cs
public class BootstrapCommands : SimpleCommand {
    /// <summary>
    /// 执行启动命令
    /// </summary>
    /// <param name="notification"></param>
    public override void Execute(INotification notification) {
        //-----------------关联命令-----------------------
        Facade.RegisterCommand(NotiConst.DISPATCH_MESSAGE, typeof(SocketCommand));

        //-----------------初始化管理器-----------------------
        Facade.AddManager(ManagerName.Lua, new LuaScriptMgr());

        Facade.AddManager<PanelManager>(ManagerName.Panel);
        Facade.AddManager<MusicManager>(ManagerName.Music);
        Facade.AddManager<TimerManager>(ManagerName.Timer);
        Facade.AddManager<NetworkManager>(ManagerName.Network);
        Facade.AddManager<ResourceManager>(ManagerName.Resource);
        Facade.AddManager<ThreadManager>(ManagerName.Thread);

        Facade.AddManager<GameManager>(ManagerName.Game);
        Debug.Log("SimpleFramework StartUp-------->>>>>");
    }
}
```

所有关于lua代码和资源更新的操作全部放在GameManager.cs类中实现.下面着重介绍GameManager中的OnResourceInited方法, 因为关于lua的代码加载都在这一块里面.

```c#
// 在资源检查更新, 加载完毕之后调用的这个方法.
public void OnResourceInited() {
  // Start会初始化Lua的环境, LuaManager在GameManager的父类BaseBehavior中被实例化.
  LuaManager.Start(); 
  LuaManager.DoFile("Logic/Network");      //加载目录lua/Logic下的Network文件中的lua代码(为什么会加载到,也就是路径查找方式是怎么样的, 这个要研究一下)
  LuaManager.DoFile("Logic/GameManager");   //加载lua/Logic下的GameManager文件中的lua代码, 这里也是关键点,可以通过加载不同的GameManager文件来返回不同的需要被显示的Panel, 关键点是让其成为一个变量,可以以书名作为上层目录,书本内容作为目录,也可以通过一个变量来指定当前的lua工作目录,然后再加指定的lua脚本.
  initialize = true;  // 设置标志, 继承自LuaBehavior中的实例, 在调用Lua中的方式时会用于判断时候可以调用Lua方法.

  NetManager.OnInit();    //初始化网络

  object[] panels = CallMethod("LuaScriptPanel");   // 这里就是调用lua代码GameManger中的LuaScriptPanel方法, 这个方法会返回需要加载的对应的lua代码中的view层的lua代码文件名.
  //---------------------Lua面板---------------------------
  foreach (object o in panels) {  // 遍历LuaScriptPanel的返回值, 加载对应的Lua的view层代码文件
    string name = o.ToString().Trim();
    if (string.IsNullOrEmpty(name)) continue;
    name += "Panel";    //添加

    LuaManager.DoFile("View/" + name);  // 将对应的view层lua代码加载进来, 这里也是关键点, 怎么让其成为一个变量
    Debug.LogWarning("LoadLua---->>>>" + name + ".lua");
  }
  //------------------------------------------------------------
  CallMethod("OnInitOK");   //加载view层代码完成之后, 调用LuaScriptPanel方法中的OnInitOK方法.
}
```

```lua
// GameManger.lua代码
function GameManager.LuaScriptPanel()
	return 'Bottom','Settings','Dialog'
end

function GameManager.OnInitOK()
	AppConst.SocketPort = 2012
    AppConst.SocketAddress = "127.0.0.1"
    NetManager:SendConnect()
	
  ![[调用对应控制层的lua代码]]
	BottomCtrl.Awake()
	SettingsCtrl.Awake()
	DialogCtrl.Awake()
end
```

```lua
![[BottomCtrl控制层的实现]]
function BottomCtrl.Awake()
    ![[调用c#代码PanelManager面板管理器中的CreatePanel方法]]
	PanelManager:CreatePanel( "Bottom",this.OnCreate )
end
```

```c#
// PanelManager面板管理器中的CreatePanel方法.
// name = Bottom
// func = BottomCtrl.OnCreate()方法
public void CreatePanel(string name, LuaFunction func = null) {
  // 资源名称, 这里的name就是BottomCtrl中传过来的Bottom
  string assetName = name + "Panel";  // BottomPanel
  GameObject prefab = ResManager.LoadAsset(name, assetName);  // 加载资源, 同时会加载资源所有的依赖, 查看ResourManager.cs中的源码. 下面会介绍
  
  // Parent是顶层的Canvas对象. 判断是否已经加载出来
  if (Parent.FindChild(name) != null || prefab == null) {
    return;
  }
  // 实例化资源
  GameObject go = Instantiate(prefab) as GameObject;
  go.name = assetName;
  go.layer = LayerMask.NameToLayer("Default");
  go.transform.SetParent(Parent);
  go.transform.localScale = Vector3.one;
  go.transform.localPosition = Vector3.zero;
  go.AddComponent<LuaBehaviour>();  // 这里会将LuaBehaviour绑定到资源对象Panel上,LuaBehavious对象会调用对应的lua的view层代码, 将这里的BottomPanel显示出来.

  if (func != null) func.Call(go);  // 这里会调用Lua代码穿过的OnCreate方法.也就是BottomCtrl中的OnCreate方法, 主要作用的给BottomPanel里面的子元素增加事件监听等操作.
  Debug.LogWarning("CreatePanel::>> " + name + " " + prefab);
}
```

```c#
// LuaBehaviour.cs,上面提到的, 在CreatePanel创建Panel的是挂载到Panel上的对象.
public class LuaBehaviour : BehaviourBase {
        private string data = null;
        private List<LuaFunction> buttons = new List<LuaFunction>();
        protected static bool initialize = false;

        protected void Awake() {
            // 调用Awake方法, 这里要注意的是调用的是哪个Lua脚本的Awake方法
            // 根据gameObject, 因为这个LuaBehavior是挂载到了上面提到的CreatePanel中创建的Panel(也就是BottomPanel)上面, 那么这里调用的就是BottomPanel.Awake方法.对应BottomPanel.lua脚本中Awake方法, 这里会将BottomPanel对象的子对象加载出来并赋给BottomPanel的对应属性, 此时BottomPanel还不会显示出来
            CallMethod("Awake", gameObject);
        }

        protected void Start() {
            // 将显示元素传入lua中,由lua来控制BottomPanel的显示.
            if (LuaManager != null && initialize) {
                LuaState l = LuaManager.lua;
                l[name + ".transform"] = transform;
                l[name + ".gameObject"] = gameObject;
            }
            CallMethod("Start");
        }

        protected void OnClick() {
            CallMethod("OnClick");
        }

        protected void OnClickEvent(GameObject go) {
            CallMethod("OnClick", go);
        }

        /// <summary>
        /// 添加单击事件
        /// </summary>
        public void AddClick(GameObject go, LuaFunction luafunc) {
            if (go == null) return;
            buttons.Add(luafunc);
            go.GetComponent<Button>().onClick.AddListener(
                delegate() {
                    luafunc.Call(go);
                }
            );
        }

        /// <summary>
        /// 清除单击事件
        /// </summary>
        public void ClearClick() {
            for (int i = 0; i < buttons.Count; i++) {
                if (buttons[i] != null) {
                    buttons[i].Dispose();
                    buttons[i] = null;
                }
            }
        }

        /// <summary>
        /// 执行Lua方法
        /// </summary>
        protected object[] CallMethod(string func, params object[] args) {
            if (!initialize) return null;
            return Util.CallMethod(name, func, args);
        }

        //-----------------------------------------------------------------
        protected void OnDestroy() {
            ClearClick();
            LuaManager = null;
            Util.ClearMemory();
            Debug.Log("~" + name + " was destroy!");
        }
    }
```

```lua
![[BottomCtrl.lua中的OnCreate方法, 就是上面提到的在加载完LuaBehavior之后, 会执行这个方法]]
function BottomCtrl.OnCreate(obj)
	gameObject = obj
	transform = gameObject.transform   ![[赋值BottomPanel的transtorm属性]]
	
	lua = gameObject:GetComponent("LuaBehaviour")  ![[获取在C#代码中挂载到BottomPanel上的LuaBehavior对象]]
	lua:AddClick( BottomPanel.buttonDialog,this.OnButtonDialogClick )  ![[添加点击事件]]
	lua:AddClick( BottomPanel.buttonPeople,this.OnButtonPeopleClick )
	lua:AddClick( BottomPanel.buttonSettings,this.OnButtonSettingsClick )
	
end
```

```c#
// ResourceManager.cs源代码
public class ResourceManager : BehaviourBase {
    private string[] m_Variants = { };
    private AssetBundleManifest manifest;
    private AssetBundle shared, assetbundle;
    private Dictionary<string, AssetBundle> bundles;

    void Awake() {
        Initialize();
    }

    /// <summary>
    /// 初始化, 这里的初始化在AppFacade.StartUp()方法被调用的时候就被执行了
    /// </summary>
    void Initialize() {
        byte[] stream = null;
        string uri = string.Empty;
        bundles = new Dictionary<string, AssetBundle>();
        uri = Util.DataPath + AppConst.AssetDirname;  // 这里也是关键点, (怎么指定来加载不同书本或者书本目录, 可以将AppConst.AsserDirName设置为非常量, 让其可变)
        stream = File.ReadAllBytes(uri);
      // 加载清单文件, 这里也是关键, 这里是根据资源打包时的目录来获取的,只要指定了目录位置, 获取方式是固定的
        assetbundle = AssetBundle.CreateFromMemoryImmediate(stream);
      // 也就是这句代码是不用变化的
        manifest = assetbundle.LoadAsset<AssetBundleManifest>("AssetBundleManifest");  
    }

    /// <summary>
    /// 载入素材, 这个方法会在PanelManager.CreatePanel()方法中被调用
    /// </summary>
    public GameObject LoadAsset(string abname, string assetname) {
        abname = abname.ToLower();
        AssetBundle bundle = LoadAssetBundle(abname);
        return bundle.LoadAsset<GameObject>(assetname);
    }

    /// <summary>
    /// 载入素材
    /// </summary>
    public void LoadAsset(string abname, string assetname, LuaFunction func) {
        abname = abname.ToLower();
        StartCoroutine(OnLoadAsset(abname, assetname, func));
    }

    IEnumerator OnLoadAsset(string abname, string assetName, LuaFunction func) {
        yield return new WaitForEndOfFrame();
        GameObject go = LoadAsset(abname, assetName);
        if (func != null) func.Call(go);
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
            string uri = Util.DataPath + abname;
            Debug.LogWarning("LoadFile::>> " + uri);
            LoadDependencies(abname);  // 载入资源的依赖, 这里也是一个加载资源的关键点

            stream = File.ReadAllBytes(uri);
            bundle = AssetBundle.CreateFromMemoryImmediate(stream); //关联数据的素材绑定
            bundles.Add(abname, bundle);
        } else {
            bundles.TryGetValue(abname, out bundle);
        }
        return bundle;
    }

    /// <summary>
    /// 载入依赖
    /// </summary>
    /// <param name="name"></param>
    void LoadDependencies(string name) {
       // 通过manifest清单文件来载入资源需要的依赖资源, manifest的初始化在Initialize方法中执行
        if (manifest == null) {
            Debug.LogError("Please initialize AssetBundleManifest by calling AssetBundleManager.Initialize()");
            return;
        }
        // Get dependecies from the AssetBundleManifest object..
      // 通过资源名称获取资源的所有相关依赖
        string[] dependencies = manifest.GetAllDependencies(name);
        if (dependencies.Length == 0) return;

        for (int i = 0; i < dependencies.Length; i++)
            dependencies[i] = RemapVariantName(dependencies[i]);

        // Record and load all dependencies.
        for (int i = 0; i < dependencies.Length; i++) {
          // 加载所有的依赖资源
            LoadAssetBundle(dependencies[i]);
        }
    }

    // Remaps the asset bundle name to the best fitting asset bundle variant.
    string RemapVariantName(string assetBundleName) {
        string[] bundlesWithVariant = manifest.GetAllAssetBundlesWithVariant();

        // If the asset bundle doesn't have variant, simply return.
        if (System.Array.IndexOf(bundlesWithVariant, assetBundleName) < 0)
            return assetBundleName;

        string[] split = assetBundleName.Split('.');

        int bestFit = int.MaxValue;
        int bestFitIndex = -1;
        // Loop all the assetBundles with variant to find the best fit variant assetBundle.
        for (int i = 0; i < bundlesWithVariant.Length; i++) {
            string[] curSplit = bundlesWithVariant[i].Split('.');
            if (curSplit[0] != split[0])
                continue;

            int found = System.Array.IndexOf(m_Variants, curSplit[1]);
            if (found != -1 && found < bestFit) {
                bestFit = found;
                bestFitIndex = i;
            }
        }
        if (bestFitIndex != -1)
            return bundlesWithVariant[bestFitIndex];
        else
            return assetBundleName;
    }

    /// <summary>
    /// 销毁资源
    /// </summary>
    void OnDestroy() {
        if (shared != null) shared.Unload(true);
        if (manifest != null) manifest = null;
        Debug.Log("~ResourceManager was destroy!");
    }
}
```

### 总结

所以，总体来说，simpleFramework我们可以直接搬过来使用， 而我们要做的只不过是额外的加一些控制变量来控制需要加载的lua脚本的位置和资源所在的目录位置而已（**我认为这一点相对简单，我们只需要添加一个类：CurrentOperateDir.cs, 里面只需要添加两个变量来记录当前的lua脚本加载目录位置和资源的加载位置即可,同时修改GameManager类和ResourceManager类中的加载位置;  那么什么时候记录这个位置呢, 也很简单,在点击识别的时候(点击眼睛图标)记录这个目录即可, 同时因为GameManager和ResourceManager等Manager类是在GlobalManager加载的时候被加载初始化的, 所以我们每次推出和进入识别场景的时候,要销毁和重建这个GlobalManager**）。同时有一点：就是所有的资源和lua脚本都需要分开，lua脚本不需要打包成AssertBundle, 只需要将资源打包即可。还有一点就是lua脚本还资源文件的一个命名规范需要确定，否则资源和lua脚本一多容易造成混淆。