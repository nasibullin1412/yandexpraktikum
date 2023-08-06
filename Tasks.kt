/**
 * 1. В переменной history нет смысла, поэтому лучше её убрать, а внутри лямбды let дать имя параметру history
 */
fun setPreferencesListener(sharedPrefs: SharedPreferences) {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == HISTORY_LIST_KEY) {
            sharedPreferences.getString(HISTORY_LIST_KEY, null)?.let { history ->
                tracksInHistory.clear()
                tracksInHistory.addAll(searchHistory.createTracksListFromJson(history))
                searchHistoryAdapter.notifyDataSetChanged()
            }
        }
    }
    sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
}

/**
 * 2.1 Если возможно изменить параметр функции, то лучше сделать enum, который и использовать в дальнейшем в логике, а print("No valid direction specified")
 * переложить на сторону вызывающую turnTo, чтобы проще было отслеживать, где появляется ошибка в логике.
 */
enum class WorldSide {
    NORTH,
    EAST,
    SOUTH,
    WEST
}

fun turnTo(direction: WorldSide) = when (direction) {
    WorldSide.NORTH -> northAction()
    WorldSide.EAST -> eastAction()
    WorldSide.SOUTH -> southAction()
    WorldSide.WEST -> westAction()
}

/**
 * 2.2 Ну или если не вариант менять тип параметра, то можно сделать хотя бы так
 */
fun turnTo(direction: String) = when (direction) {
    "North" -> northAction()
    "East" -> eastAction()
    "South" -> southAction()
    "West" -> westAction()
    else -> print("No valid direction specified")
}

/**
 * 3. StringBuilder позволяет не создавать новые объекты строк, как в случае с оператором +.
 *    По той же причине лучше вместо суммирования строк использовать шаблоны.
 */
fun createResultString(users: List<User>): String = StringBuilder().apply {
    users.forEach { append("Имя: ${it.name}, Возраст: ${it.age} \n") }
}.toString()


/**
 * 4.  Правки:
 *     1) Лучше сделать интерфейс AppPreferences, чтобы была возможность использовать его в других классах. Иначе нельзя будет покрыть классы,
 *        которые используют AppPreferences, юнит тестами, а так AppPreferences всегда можно застабать для тестов.
 *     2) При изменении префов стоит использовать apply, чтобы изменения в них фиксировались.
 *     3) У promoWasShown в set, во-первых, надо поправить edit (он не принимает на вход лямбду), во-вторых, надо поменять ключ,
 *        по которому происходит перезапись значения. В исходной версии значение меняется по ключу ONBOARDING_KEY, а должно по PROMO_KEY.
 *     4) На любителя, но можно сделать private companion object (так как он содержит только private поля)
 */

interface AppPreferences {
    var onboardingWasShown: Boolean

    var promoWasShown: Boolean
}

internal class AppPreferencesImpl(private val preferences: SharedPreferences) : AppPreferences {

    private companion object {
        const val ONBOARDING_KEY = "ONBOARDING_KEY"
        const val PROMO_KEY = "PROMO_KEY"
    }

    override var onboardingWasShown: Boolean
        get() = preferences.getBoolean(ONBOARDING_KEY, false)
        set(value) {
            preferences.edit().putBoolean(ONBOARDING_KEY, value).apply()
        }

    override var promoWasShown: Boolean
        get() = preferences.getBoolean(PROMO_KEY, false)
        set(value) {
            preferences.edit().putBoolean(PROMO_KEY, value).apply()
        }
}


/**
 * 5. Правки:
 *      1) На любителя, но можно сделать private companion object (так как он содержит только private поля)
 *      2) Стоит вынести логику таймера из колбэка жизненного цикла
 *      3) Стоит посмотреть на требования, но мне кажется, что лучше переместить таймер в колбэк onResume,
 *         так как этот колбэк вызывается при возможности взаимодействия клиента с пользовательским интерфейсом, следовательно он больше подходит для запуска таймера
 *      4) С вьюхами лучше использовать viewBinding, так как он безопасней и удобней, но тут оставил findViewById
 *      5) Поле textView больше нигде не используется, кроме как в колбэках таймера, поэтому стоит убрать это поле.
 *      6) Стоит стартовать таймер, только если существует поле с айдишником text, иначе смысла в таймере нет, поэтому лучше занести его под let
 *      7) Чтобы не работало несколько таймеров при переоткрытии экрана с TimerActivity, необходимо отменять таймер при закрытии экрана. Так как
 *         запуск таймера производится в onResume, то остановку таймера лучше производить в onPause. Преимуществом onPause также является то, что
 *         этот колбэк гарантировано будет вызван, даже если процесс приложения будет уничтожен системой. Однако стоит учитывать, что onPause
 *         вызывается при любом переходе экрана активити на задний план, даже при появлении диалогового окошка. Если таймер при этом не должен сбрасываться
 *         то лучше перенести запуск и остановку таймера в колбэки onStart/onStop.
 *      7) Чтобы избежать потенциальных утечек памяти CountDownTimer лучше поместить в поле, которая будет зануляться при закрытии экрана
 *      8) Если в требовании к экрану указано, что необходимо продолжать работу таймера при смене конфигурации (язык, ориентация и т.д.), открытии другого экрана
 *         по верх текущего и т.п, то необходимо воспользоваться колбэком onSaveInstanceState(), и добавить запуск таймера в onCreate с предварительной проверкой бандла.
 *         Предлагаю именно onSaveInstanceState, так как надо сохранить небольшое количество данных с базовым типом. Можно также использовать viewModel
 *         и держать оставшееся время в нём.
 *      9) this@TimerActivityIn -> this@TimerActivity
 */

class TimerActivity : AppCompatActivity() {

    private companion object {
        const val MILLIS_IN_SECONDS = 1000L
        const val INTERVAL = 1 * MILLIS_IN_SECONDS
        const val TIME = 100 * MILLIS_IN_SECONDS
    }

    private var countDownTimer: CountDownTimer? = null

    override fun onResume() {
        super.onResume()
        startTimer()
    }

    override fun onPause() {
        super.onPause()
        stopTimer()
    }

    private fun startTimer() {
        findViewById<TextView>(R.id.text)?.let {
            countDownTimer = object : CountDownTimer(TIME, INTERVAL) {

                override fun onTick(millisUntilFinished: Long) {
                    it.text = getString(R.string.millis_until_finished, millisUntilFinished.toString())
                }

                override fun onFinish() {
                    Toast.makeText(this@TimerActivity, R.string.timer_is_finished, Toast.LENGTH_SHORT).show()
                }
            }.start()
        }
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }
}

/**
 * 6. Правки:
 *      Одной проверки прав isPermissionGranted не хватит для получения локации. Начиная с 6 версии Android (API 23) помимо uses-permission необходимо динамически
 *      запрашивать права в рантайме. Поэтому надо добавить в код запрос необходимых разрешений, а также будет не лишним накинуть диалог, который
 *      расскажет пользователю, зачем приложению требуется разрешение на получение текущей геопозиции. Возможно, ещё стоит запрашивать локацию не в главном потоке,
 *      а использовать для этого корутины, как в статье https://medium.com/androiddevelopers/simplifying-apis-with-coroutines-and-flow-a6fb65338765
 */
class CustomActivity : AppCompatActivity() {

    private val locationCallback = CustomLocationCallback()
    private var locationClient: FusedLocationProviderClient? = null
    private val locationRequest: LocationRequest = LocationRequest.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        if (isPermissionGranted()) {
            locationClient?.run {
                lastLocation.addOnSuccessListener(::onLastLocation)
                requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
        }
    }

    override fun onPause() {
        super.onDestroy()
        if (isPermissionGranted()) {
            locationClient!!.removeLocationUpdates(locationCallback)
        }
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Проверка на то, стоит ли показывать диалог с объяснением необходимости разрешения на получение геопозиции
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed")
                    .setMessage("This app needs the Location permission, please accept to use location functionality")
                    .setPositiveButton(
                        "OK"
                    ) { _, _ ->
                        requestLocationPermission()
                    }
                    .create()
                    .show()
            } else {
                requestLocationPermission()
            }
        } else {
            checkBackgroundLocation()
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            MY_PERMISSIONS_REQUEST_LOCATION
        )
    }

    private fun checkBackgroundLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBackgroundLocationPermission()
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                MY_PERMISSIONS_REQUEST_BACKGROUND_LOCATION
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MY_PERMISSIONS_REQUEST_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != MY_PERMISSIONS_REQUEST_LOCATION && requestCode != MY_PERMISSIONS_REQUEST_BACKGROUND_LOCATION) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationClient?.run {
                    lastLocation.addOnSuccessListener(::onLastLocation)
                    requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.getMainLooper()
                    )
                }
            }
            if (requestCode != MY_PERMISSIONS_REQUEST_BACKGROUND_LOCATION) checkBackgroundLocation()
        } else {
            Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show()
        }
    }

    private fun onLastLocation(location: Location) {
        ...
    }

    private fun isPermissionGranted(): Boolean {
        ...
    }

    private companion object {
        const val MY_PERMISSIONS_REQUEST_LOCATION = 99
        const val MY_PERMISSIONS_REQUEST_BACKGROUND_LOCATION = 66
    }
}


/**
 * 7. Если список должен меняться, то лучше реализовать абстрактный класс ListAdapter, который под капотом использует DiffUtil для пересборки контента при изменении списка.
 *    DiffUtill позволяет пересобирать только те ViewHolder, элементы которых изменились в списке. Это оптимизирует работу RecyclerView при изменениях в списке и
 *    избавляет от подёргиваний контента, которые заметны глазу пользователя, при обновлении списка RecyclerView.
 *    Обновлять список можно вот так: adapter.submitList(list).
 */

internal class UserCallback : DiffUtil.ItemCallback<User>() {
    override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem == newItem // будет работать как ожидается, если User - data class
    }
}

internal class UserAdapter(items: List<User>) : ListAdapter<User, UserAdapter.ViewHolder>(UserCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = inflateView(parent, viewType)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun inflateView(parent: ViewGroup, viewType: Int): View {
        ...
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(user: User) {
            ...
        }
    }
}

/**
 * 8.
 * Привет! Прекрасно понимаю тебя, сам в своё время голову не сломал, пока пытался понять эту тему. Её действительно сложно переварить
 * через чтение документаций или статей с теорией, так что давай для начала попробую объяснить на пальцах.
 */

/**
 * Представь, что нам надо спроектировать абстракцию для работы ветеренарной клиники - VetClinic. В клинике могут
 * лечить собак и кошек. Пусть это будут классы Cat, Dog. Чтобы зверушки не умерли от голода их стоит
 * переодически кормить, поэтому в первую очередь нам надо решить вопрос с питанием в клинике. Добавляем методы eat в класс
 * каждого животного и методы кормёжки в класс VetClinic:
 */

class Cat {
    //...
    void eat() {
        //....
    }
    //....
}

class Dog {
    //...
    void eat() {
        //....
    }
    //....
}

class VetClinic {
    void feedingCat(List<Dog> dogs) {
        for (int i = 0; i < dogs.size(); i++) {
            dogs.get(i).eat();
        }
    }

    void feedingDogs(List<Cat> cats) {
        for (int i = 0; i < cats.size(); i++) {
            cats.get(i).eat();
        }
    }
}
/**
 * Сразу бросается в глаза, что реализация метода кормёжки для каждого из животного схожа. Да и каждый раз добавлять для нового животного
 * отдельный метод кормёжки не лучшее решение. Первое решение, которое приходит на ум, создать абстрактный класс для всех животных Animal и выделить туда
 * общие методы признаки животных, например, метод eat.
 */
abstract class Animal {
    abstract void eat();
}

class Cat extends Animal {
    @Override
    void eat() {}
}

class Dog extends Animal {
    @Override
    void eat() {}
}

/**
 * Класс VetClinic тогда интуитивно хочется переписать с использованием Animal
 */
class VetClinic {
    //...
    void feedingAnimals(List<Animal> animals) {
        for (int i = 0; i < animals.size(); i++) {
            animals.get(i).eat();
        }
    }
}
/**
 * Несмотря на то, что всё как будто выглядит хорошо, при попытке вызова метода feeding для классов Cat и Dog будет ошибка компилятора.
 */
Cat cat = new Cat();
Cat cat = new Cat();
List<Cat> cats = new ArrayList<Cat>();
cats.add(cat);
cats.add(cat);
VetClinic vetClinic = new VetClinic();

//ошбика комплиятора
vetClinic.feedingAnimals(cats);

/**
 * Интуитивно это выглядит не логично, ведь если бы в параметре feedingAnimals был бы параметр Animal animal, то никакой ошибки компиляции
 * не было бы. Дело в том, что существует правило: "Если класс B является наследником класса А, то List<B> при этом — не наследник List<A>".
 * Почему именно таким принципом руководствовались создатели языка? Давай представим, что компилятор бы давал возможность сохранить
 * связь наследования для дженериков, тогда можно было бы сделать так
 */
List<String> strings = new ArrayList<String>();
List<Object> objects = strings;
objects.add(new Object());
String s = strings.get(0);
/**
 * Поскольку компилятор не выдал нам ошибок и позволил создать ссылку List<Object> object на коллекцию строк strings,
 * можем добавить в strings не строку, а просто любой объект Object. Таким образом, мы лишаемся гарантии того, что в нашей коллекции находятся только
 * указанные в дженерике объекты String. То есть, мы потеряли главное преимущество дженериков — типобезопасность. И раз компилятор позволил нам все это сделать,
 * значит, мы получим ошибку только во время исполнения программы, что всегда намного хуже, чем ошибка компиляции.
 * Как нам теперь покормить наших зверушек? Тут и вступают в игру wildcards.
 * Добавим конструкцию ? extends в параметр feedingAnimals
 */
class VetClinic {
    //...
    void feedingAnimals(List<? extends Animal> animals) {
        for (int i = 0; i < animals.size(); i++) {
            animals.get(i).eat();
        }
    }
}
/**
 * Ошибка компиляции магическим образом пропадает из строчки vetClinic.feedingAnimals(cats);, что же произошло? Был использован
 * один из нескольких типов wildcard - "extends" (Upper Bounded Wildcards). Это значит, что метод принимает на вход список объектов класса Animal
 * либо объектов любого класса-наследника Animal (? extends Animal). Иными словами, метод может принять на вход списки Animal, Dog, Cat — без разницы.
 */
Cat cat = new Cat();
Cat cat = new Cat();
List<Cat> cats = new ArrayList<Cat>();
cats.add(cat);
cats.add(cat);
VetClinic vetClinic = new VetClinic();
...
vetClinic.feedingAnimals(cats);
vetClinic.feedingAnimals(dogs);

/**
 * Добавим в иерархию классов Pet, который будет наследником Animal и родителем для Cat и Dog. Представим, что нам нужно
 * исключить собак из общей кормёжки и оставить её только для кошек и других животных, которые описываются классами Pet или Animal.
 * Здесь нам снова придут на помощь wildcards. Но на этот раз мы воспользуемся другим типом — “super” (другое название — Lower Bounded Wildcards).
 */
class VetClinic {
    //...
    void feedingAnimals(List<? super Cat> animals) {
        for (int i = 0; i < animals.size(); i++) {
            animals.get(i).eat();
        }
    }
}

List<Pet> pets = new ArrayList<>();
pets.add(new Pet());
pets.add(new Pet());

List<Cat> cats = new ArrayList<>();
cats.add(new Cat());
cats.add(new Cat());

List<Dog> dogs = new ArrayList<>();
dogs.add(new Dog());
dogs.add(new Dog());

//ок
iterateAnimals(pets);
iterateAnimals(cats);

//ошибка компиляции!
iterateAnimals(dogs);

/**
 * Конструкция <? super Cat> говорит компилятору, что метод feedingAnimals может принимать на вход список
 * объектов класса Cat либо любого другого класса-предка Cat. Под это описание в нашем случае подходят сам класс Cat, его предок — Pet, и предок предка — Animal.
 * Класс Dog не вписывается в это ограничение, и поэтому попытка использовать метод со списком List<Dog> приведет к ошибке компиляции.
 * Надеюсь стало чуть понятней, если будут ещё вопросы, пиши, с радостью отвечу!
 */

/**
 * 9. Привет! Изучение Java важно по нескольким причинам:
 *      - Kotlin - молодой язык, который стал популярен в Android-разработке относительно недавно. Существует множество компаний
 *        с крупными приложениями, которые появились ещё до популярности Kotlin, и не все ещё успели полностью перейти на него, а некоторые
 *        и не хотят этого делать, например Telegram. Поэтому существует высокая вероятность того, что ты встретишься с Java при выполнении
 *        продуктовых и технических задач.
 *      - Многие классы Android SDK, реализованы на Java. Например, Ativity, Fragment и т.д. Часто в работе приходится лезть в исходники,
 *        так как документация не даёт полной картины о работе класса.
 *      - Фреймворки, которые осуществляют кодогенерацию, чаще всего делают это на Java. Ярким примером является Dagger. В большом проекте иногда
 *        приходится лезть в то, что нагенерировал Dagger и разбираться в чём проблема.
 *      - Байткод Kotlin декомпилируется в Java, иногда бывает полезно заглянуть в него. Например, мне таким образом удалось обнаружить утечку памяти,
 *        предвидеть которую в коде на Kotlin было сложно.
 *     Всё это ведёт к тому, что знание языка Java и умение его примернить на практике является обязательным навыком для специалиста, который хочет
 *     соответствовать уровню Middle-разработчика.
 */
