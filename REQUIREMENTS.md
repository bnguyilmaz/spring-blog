# Çoklu kullanıcı, Last.fm yetkilendirmesi ve profil ziyaretleri

Durum: Aşağıdaki akış uygulandı; açık ürün kararları ayrıca listelenmiştir.
Tarih: 28 Ekim 2026. Branch: `feature/multi-user-music`.

## Kimlikler ve yetkiler

- Google `sub`: Uygulamaya giriş yapan kişiyi tanımlar.
- Uygulama kullanıcı adı: Diğer kullanıcıların profil ararken kullandığı addır.
- Last.fm kullanıcı adı: Yetkilendirme yanıtından alınan harici hesap adıdır.
- Giriş yapan kullanıcı diğer kullanıcıların profillerini ve doğrulanmış Last.fm müzik seçkilerini görüntüleyebilir.
- Last.fm bağlantısını oluşturma veya değiştirme yetkisi yalnızca aktif uygulama kullanıcısına aittir. Ziyaret edilen kişi, bağlama işleminin hedefi olarak seçilemez.

## Yerel kurulum

Proje kökündeki `.env` dosyasına API hesabının iki değerini birlikte ekle:

```properties
LASTFM_API_KEY=api_key_degeri
LASTFM_API_SECRET=shared_secret_degeri
LASTFM_CALLBACK_URL=http://localhost:8084/lastfm/callback
```

Mevcut `DB_PASSWORD` değerini koru. [.env.example](.env.example) örnek yapılandırmadır; gerçek değerler yerel `.env` dosyasında kalır. Spring, bu dosyayı `spring.config.import` ile okur. Ortam değişkenleriyle yapılandırma da desteklenir. Uygulamayı proje kökünden başlat.

Last.fm API uygulamasında callback adresi `http://localhost:8084/lastfm/callback` olarak ayarlanmalıdır. Backend ayrıca yetkilendirme isteğinde `cb` parametresine aynı adresi ve işlemin rastgele `state` bilgisini ekler. Canlı ortamda HTTPS callback kullanılmalıdır.

## Akış

```mermaid
flowchart TD
    A[Google ile giriş] --> B{Uygulama kaydı var mı?}
    B -- Hayır --> C[Uygulama kullanıcı adı seç]
    C --> D[Profil]
    B -- Evet --> D
    D --> E[POST /lastfm/connect]
    E --> F[Last.fm giriş ve izin ekranı]
    F --> G[GET /lastfm/callback: state ve token]
    G --> H{Oturum, Google kimliği, süre ve tek kullanım uygun mu?}
    H -- Hayır --> I[Ayarlara dön ve yeniden başlat mesajı göster]
    H -- Evet --> J[İmzalı auth.getSession çağrısı]
    J --> K{Geçerli kullanıcı adı ve session key döndü mü?}
    K -- Hayır --> I
    K -- Evet --> L[Doğrulanan adı ve verified_at bilgisini kaydet]
    L --> M[Kendi /music sayfası]
    D --> N[Keşfet: uygulama kullanıcı adını ara]
    N --> O[/users/username]
    O --> P[/users/username/music]
```

Girişten sonra başlangıç sayfası profildir. Last.fm bağlamak uygulamaya kayıt olmanın koşulu değildir. Kullanıcı kendi hesabını bağlamadan başkasının doğrulanmış müzik seçkisini görebilir.

## Endpoint sözleşmesi

Aşağıdaki endpoint'ler giriş gerektirir. Ana sayfa `/` ve `/home` herkese açıktır.

| Metot ve endpoint | Davranış |
| --- | --- |
| `GET /oauth-user` | Kendi profilin; kayıt yoksa kullanıcı adı seçimine yönlendirir. |
| `GET/POST /choose-username` | Mevcut Google kayıt akışını tamamlar. |
| `GET /profile/lastfm` | Kendi doğrulanmış bağlantını ve bağlama/değiştirme düğmesini gösterir. |
| `POST /lastfm/connect` | CSRF kontrolünden sonra aktif kullanıcı adına Last.fm yetkilendirmesini başlatır. |
| `GET /lastfm/callback` | Token ve state kontrolünü tamamlar; başarıda `/music`, hatada ayarlar veya kayıt akışına yönlendirir. |
| `GET /music` | Kendi doğrulanmış müziklerin; bağlantı yoksa ayarlara, uygulama kaydı yoksa kayıt akışına yönlendirir. |
| `GET /users` | Uygulama kullanıcı adıyla profil arama ekranı. |
| `GET /users?username=ayse` | `/users/ayse` adresine yönlendirir. Kullanıcı adı biçimine uymayan yollar/URL'ler reddedilir. |
| `GET /users/{username}` | Hedef uygulama kullanıcısının ziyaretçi profilini gösterir; bilinmeyen kullanıcı 404. |
| `GET /users/{username}/music` | Hedef uygulama kullanıcısının doğrulanmış Last.fm seçkisini gösterir; bilinmeyen kullanıcı 404. |
| `GET /music/artist?name=...&profile=ayse` | Sanatçı bağlantısı; dönüş bağlantısı seçkinin ait olduğu profile gider. |
| `POST /logout` | Mevcut uygulama çıkış akışı. |

Eski `POST /profile/lastfm` kaldırıldı. Kullanıcı adını elle göndererek yetkilendirmeyi atlamak mümkün değildir.

## İş kuralları ve kabul kriterleri

| Kimlik | Kural |
| --- | --- |
| BR-01 | Uygulamaya giriş Google ile yapılır. Yeni kullanıcı 3–50 karakterlik uygulama kullanıcı adı seçer. Mevcut kullanıcı adı/e-posta/Google hesabı çakışma kontrolleri korunur. |
| BR-02 | Last.fm adı formdan alınmaz; yalnızca başarılı `auth.getSession` yanıtındaki hesap adı bağlantıya yazılır. |
| BR-03 | Bağlama işlemi sunucuda rastgele state, başlatan Google kimliği ve başlangıç zamanı ile aynı HTTP oturumunda tutulur. |
| BR-04 | İşlem 10 dakika geçerlidir. Başlatılmamış işlem, farklı oturum, farklı Google hesabı, yanlış state, eksik/boş token ve yeniden kullanım reddedilir. Token sağlayıcıya ait opak bir değerdir; hexadecimal veya sabit uzunluk varsayılmaz. Yerel olarak yalnızca 4096 karakter sınırı ve kontrol karakteri kontrolü uygulanır; geçerliliği Last.fm doğrular. |
| BR-05 | Yeni yetkilendirme başlatıldığında aynı oturumdaki önceki bekleyen işlem geçersiz olur. Başarısız değişiklik mevcut doğrulanmış bağlantıyı bozmaz. |
| BR-06 | Last.fm token'ı bir kere işlenir. Başarı veya upstream hata sonrasında tekrar denemek için yeni yetkilendirme başlatılır. Yanlış state mevcut geçerli işlemi tüketmez. |
| BR-07 | API imzası Last.fm'in tarif ettiği parametre sırası ve shared secret ile sunucuda üretilir. Secret tarayıcıya gönderilmez; HTTP hata mesajına token veya credential eklenmez. |
| BR-07a | Token değiştirilmeden imzalanır; HTTP isteğinde query parametresi olarak ayrıca kodlanır. `+`, `/`, `=`, `&`, `?` gibi karakterler token'ın değerini değiştirmemelidir. |
| BR-08 | Bir uygulama kullanıcısının en fazla bir Last.fm profil kaydı olur. Yeniden yetkilendirme aynı kaydı günceller; `connected_at` korunur, `verified_at` güncellenir. |
| BR-09 | Daha önce elle eklenmiş ve `verified_at` bilgisi olmayan kayıtlar doğrulanmış kabul edilmez. Kendi müzik ekranı yeniden bağlama ister; ziyaretçiler bu eski ada ait müzikleri göremez. |
| BR-10 | Kendi `/music` adresinde kullanıcı oturumdan belirlenir. `?user=...` hedefi değiştirmez. Başkalarını görüntülemek için açıkça `/users/{username}/music` kullanılır. |
| BR-11 | Ziyaretçi müzik ekranında hedef, URL'deki uygulama kullanıcısının veritabanındaki doğrulanmış Last.fm bağlantısıdır. URL'deki uygulama adı doğrudan Last.fm adı olarak yorumlanmaz. |
| BR-12 | Ziyaretçinin Last.fm eklememiş olması başka birinin müziklerini görmesine engel değildir. Hedef kişi bağlamamışsa açıklama gösterilir; ziyaretçi kendi bağlama ekranına yönlendirilmez. |
| BR-13 | Ziyaretçi modeline kullanıcı entity'si verilmez. Görünen profil bilgisi uygulama kullanıcı adı ve doğrulanmış Last.fm adıdır. E-posta, Google kimliği ve credential'lar verilmez. |
| BR-14 | Başkasının profilinde Last.fm bağlama/değiştirme düğmesi gösterilmez. İstek doğrudan gönderilse bile aktif uygulama kullanıcısının bağlantısı işlenir. |
| BR-15 | Son şarkılar, sanatçılar ve albümler herkese açık Last.fm okuma endpoint'lerinden alınır. API'ye `limit=12`, albüm/sanatçı için `period=1month` gönderilir. Veri ayrıca 12 elemana kırpılmaz. |
| BR-16 | Boş dinleme listesi hata değildir. Last.fm isteklerinden biri hata verirse müzik bölümleri gizlenir ve aynı kullanıcının sayfasına tekrar deneme bağlantısı gösterilir. |
| BR-17 | Müzik verileri her istekte alınır; uygulama veritabanına şarkı geçmişi kopyalanmaz ve önbellek kullanılmaz. |
| BR-18 | Last.fm'den gelen session key yanıtın doğrulanmasında kullanılır; veritabanına veya HTTP oturumuna kaydedilmez. Saklanan bilgi doğrulanan ad ve doğrulama zamanıdır. |
| BR-19 | Callback yanıtı `no-store` ve `Referrer-Policy: no-referrer` başlıklarıyla kendi temiz uygulama URL'sine yönlendirir. |
| BR-20 | Kayıtlı Last.fm adı birden fazla uygulama hesabında bulunabilir; global unique kısıtı henüz ürün kararı değildir. |

Last.fm isteği için bağlantı/okuma zaman aşımı ayrı ayrı 5 saniyedir; bu süre tüm müzik sayfasının toplam yanıt süresi değildir.

## Callback hatalarını tanılama

Callback başarıda `/music`, hatada `/profile/lastfm` adresine yönlendirir. Callback'in adres çubuğunda kalması beklenmez.

Oturum kaybı, eksik/farklı state, Google hesabı değişimi, yerel süre aşımı ve geçersiz token artık ayrı kullanıcı mesajları üretir. Sunucu günlüğünde `Last.fm authorization failed: reason=...` kaydı bulunur:

| Neden | Anlamı |
| --- | --- |
| `REQUEST_MISSING` | Aynı oturumda başlatılmış istek yok; yeniden başlatma, oturum kaybı veya tekrar callback olasılığı. |
| `STATE_MISSING` / `STATE_MISMATCH` | Dönüşte state bulunmuyor veya son başlatılan işlemle eşleşmiyor. |
| `USER_CHANGED` / `REQUEST_EXPIRED` / `TOKEN_INVALID` | Aktif kullanıcı değişmiş, 10 dakika geçmiş veya Last.fm token'ı eksik/biçimsiz. |
| `UPSTREAM_API_ERROR` | Last.fm isteği reddetti. Kayda yalnızca HTTP durumu ve sayısal API hata kodu eklenir. |
| `UPSTREAM_TRANSPORT_ERROR` | HTTP bağlantısı/yanıtın okunması başarısız; kayıtta yalnızca exception sınıfı vardır. |
| `RESPONSE_INVALID` | Beklenen kullanıcı adı ve session key yapısı alınamadı. |

Ham callback URL'si, token, API key, secret, kullanıcı kimliği ve yanıt gövdesi bu kayıtlara yazılmaz. HTTP 4xx içindeki API hata kodları da ayrıştırılır. Tanılama değişikliği oturum ve sahiplik kontrollerini gevşetmez.

## Veri değişikliği ve mevcut kullanıcılar

`lastfm_profiles` tablosuna nullable `verified_at` alanı eklendi. Projedeki `spring.jpa.hibernate.ddl-auto=update` ayarıyla uygulama açılışında alan eklenir. Elle yönetilen bir şemada eşdeğer değişiklik:

```sql
ALTER TABLE lastfm_profiles ADD COLUMN IF NOT EXISTS verified_at timestamp;
```

Eski satırlar silinmez ve otomatik doğrulanmış işaretlenmez. Kullanıcı Last.fm üzerinden yeniden bağlandığında mevcut satırı doğrulanır/güncellenir.

Yetkilendirme, hesabın bağlandığı anda kontrol edildiğini kanıtlar. Last.fm tarafındaki iznin sonradan geri alınmasını izleme veya yerel bağlantıyı otomatik kaldırma özelliği bu sürümde yoktur.

## Test kanıtı

Son tam çalıştırma: **144 test, 0 başarısızlık, 0 hata**. Sınıf bazında sonuçlar `target/surefire-reports` altında bulunur. Önceki sürümün 96 testi ile yeni akışın sonuçları birbirine karıştırılmamalıdır.

| Test sınıfı | Kapsam |
| --- | --- |
| `LastFmAuthorizationServiceTest` | Yetkilendirme URL'si, rastgele state, imza, doğrulanan hesap adı, eksik yapılandırma, oturum/Google kimliği/state eşleşmesi, süre, tekrar kullanım, yeni işlemle öncekinin geçersizleşmesi, boş/kontrol karakterli/aşırı uzun token, hexadecimal olmayan ve farklı uzunluktaki opak token, özel karakterlerin URL'de korunması, API/HTTP/JSON hata yanıtları ve credential içermeyen hata mesajları. |
| `LastFmProfileServiceTest` | Eski manuel kaydın doğrulanmamış sayılması, doğrulama zamanıyla kayıt, mevcut satırı güncelleme ve kayıtsız kullanıcının reddi. |
| `MusicProfileTest` | Gerçek authorization servisi + gerçek profil servisi + MockMvc ile başlangıç/callback/kayıt akışı; ziyaretçi ayrımı, kendi müzik kısayolu, eski kayıtlar, gizli alanların gösterilmemesi, CSRF, hata/tekrar deneme, hesap değiştirme, eksik izin ve tekrar callback. |
| `LastFmRouteContractTest` | Manuel girişin kaldırılması ve yetkilendirme düğmesi; başka kullanıcı profili; bağlanmamış ziyaretçi müzik ekranı. |
| `UserRepositoryTest` | Testcontainers PostgreSQL üzerinde kullanıcı/Last.fm eşleşmesi, uygulama kullanıcı adıyla sorgu, doğrulama zamanının kalıcılığı ve eski kaydın doğrulanmamış kalması. |
| `LastFmServiceTest` | Okuma istekleri, gerçek JSON dönüşümü, farklı kullanıcıların sorgulanması, boş geçmiş ve API/HTTP hata davranışı. |
| `NavigationTest`, `PageRenderingTest` | Ana sayfa, profil, yetkilendirme düğmeleri, ayarlar, kullanıcı adı seçimi, dolu müzik ekranı, metin kaçışlama ve kapak alternatifi. |
| Mevcut Auth/UserService/API testleri | Google kayıt, kullanıcı adı önerisi/doğrulaması, çakışmalar ve mevcut API davranışları. |

Testlerde Last.fm HTTP yanıtları taklit edilir; gerçek API anahtarı veya kullanıcı izni kullanılmaz. PostgreSQL testleri gerçek geçici veritabanı çalıştırır. HTML render testleri tarayıcıdaki piksel görünümü veya gerçek kullanıcıyla Google/Last.fm girişinin yerine geçmez.

## Açık ürün kararları ve ek işler

- Aynı Last.fm hesabının birden fazla uygulama hesabına bağlanmasına izin verilecek mi? Mevcut davranış izin verir.
- Kullanıcı bağlantısını tamamen kaldırabilsin mi? Bu sürümde değiştirme var, kaldırma yok.
- Profiller anonim ziyaretçiye de açılsın mı? Mevcut erişim yalnızca giriş yapan kullanıcılardır.
- Müzik API'sinde kısmi hata durumunda başarılı bölümler gösterilsin mi? Mevcut davranış tüm bölümleri gizler.
- Uygulama kullanıcı adlarının büyük/küçük harf eşitliği ayrıca kararlaştırılmalı.
- Google kayıt akışında bekleyen kayıt kimliğinin aktif Google kullanıcısıyla eşleştirilmesi ayrıca ele alınmalı. Last.fm callback'i bu eşleşmeyi zaten yapar.
- Uygulama kullanıcı adı biçimi için servisten gelen `IllegalArgumentException` yanıtının forma dönmesi mevcut Google kayıt akışında ayrıca ele alınmalı.
- Aynı kullanıcı için eşzamanlı iki ilk profil kaydında veritabanı unique hatasının kullanıcıya gösterimi ayrıca test edilmeli.
- Gerçek Last.fm hesabıyla yerel callback, Google oturumu ve izin reddi akışı elle doğrulanmalı.

Resmî kaynaklar: [Last.fm web yetkilendirmesi](https://www.last.fm/api/webauth), [auth.getSession](https://www.last.fm/api/show/auth.getSession), [imza kuralları](https://www.last.fm/api/authspec).
