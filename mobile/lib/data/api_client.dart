import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:dio/dio.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../core/config.dart';
import '../core/formatters.dart';
import '../core/l10n.dart';
import 'models.dart';
import 'search_request.dart';

/// Error returned by the API, with the rule violations explaining why.
class ApiException implements Exception {
  ApiException(this.code, this.message, {this.status, this.violations = const [], this.details});

  final String code;
  final String message;
  final int? status;
  final List<Violation> violations;
  final Map<String, dynamic>? details;

  bool get isOffline => code == 'OFFLINE';

  @override
  String toString() => message;
}

class ApiClient {
  /// [clientId] is a random per-install id: rate limits are per IP *and* install, because mobile
  /// carriers share one public IP between thousands of subscribers (CGNAT).
  ApiClient({Dio? dio, String? clientId})
      : _dio = dio ??
            Dio(
              BaseOptions(
                baseUrl: AppConfig.apiBaseUrl,
                connectTimeout: const Duration(seconds: 8),
                receiveTimeout: const Duration(seconds: 20),
                sendTimeout: const Duration(seconds: 10),
                contentType: Headers.jsonContentType,
                responseType: ResponseType.json,
                headers: {'X-Wave-Client': ?clientId},
              ),
            ) {
    _dio.interceptors.add(_RetryInterceptor(_dio));
  }

  final Dio _dio;
  String? _etag;
  Meta? _meta;

  Future<T> _call<T>(Future<Response<dynamic>> Function() request, T Function(dynamic data) parse) async {
    try {
      final response = await request();
      return parse(response.data);
    } on DioException catch (e) {
      throw _map(e);
    }
  }

  ApiException _map(DioException e) {
    final data = e.response?.data;
    if (data is Map<String, dynamic> && data['code'] is String) {
      return ApiException(
        data['code'] as String,
        data['message'] as String? ?? 'Error',
        status: e.response?.statusCode,
        violations: (data['violations'] as List<dynamic>? ?? const []).map((v) => Violation.fromJson(v as Json)).toList(),
        details: data['details'] as Map<String, dynamic>?,
      );
    }
    if (e.type == DioExceptionType.connectionError || e.type == DioExceptionType.connectionTimeout || e.response == null) {
      return ApiException('OFFLINE', 'offline');
    }
    return ApiException('HTTP_${e.response?.statusCode}', e.message ?? 'HTTP error', status: e.response?.statusCode);
  }

  /// Catalog metadata, revalidated with ETag so repeat launches cost a 304.
  Future<Meta> meta() async {
    try {
      final response = await _dio.get<dynamic>(
        '/api/v1/meta',
        options: Options(
          headers: {if (_etag != null) 'If-None-Match': _etag},
          validateStatus: (s) => s != null && (s == 304 || (s >= 200 && s < 300)),
        ),
      );
      if (response.statusCode == 304 && _meta != null) return _meta!;
      _etag = response.headers.value('etag');
      return _meta = Meta.fromJson(response.data as Json);
    } on DioException catch (e) {
      throw _map(e);
    }
  }

  Future<SearchResponse> search(SearchQuery query, Currency currency, AppLang lang) => _call(
        () => _dio.post<dynamic>('/api/v1/search', data: query.toJson(currency, lang)),
        (d) => SearchResponse.fromJson(d as Json),
      );

  Future<List<DayPrice>> calendar(SearchQuery query, DateTime month, Currency currency) => _call(
        () => _dio.get<dynamic>('/api/v1/search/calendar', queryParameters: {
          'from': query.from,
          'to': query.to,
          'month': '${month.year}-${month.month.toString().padLeft(2, '0')}',
          'adults': query.passengers.adults,
          'seniors': query.passengers.seniors,
          'children': query.passengers.childrenAges.join(','),
          'vehicle': query.vehicle == null ? 'NONE' : apiName(query.vehicle!.type),
          'accommodation': apiName(query.accommodation),
          'currency': currency.name,
        }),
        (d) => (d as List<dynamic>).map((e) => DayPrice.fromJson(e as Json)).toList(),
      );

  Future<Quote> quote(Map<String, dynamic> selection) =>
      _call(() => _dio.post<dynamic>('/api/v1/quote', data: selection), (d) => Quote.fromJson(d as Json));

  Future<BookingView> createBooking(Map<String, dynamic> body, String idempotencyKey) => _call(
        () => _dio.post<dynamic>('/api/v1/bookings', data: body, options: Options(headers: {'Idempotency-Key': idempotencyKey})),
        (d) => BookingView.fromJson(d as Json),
      );

  Future<BookingView> booking(String reference, String lastName) => _call(
        () => _dio.get<dynamic>('/api/v1/bookings/$reference', queryParameters: {'lastName': lastName}),
        (d) => BookingView.fromJson(d as Json),
      );

  Future<PaymentInitiation> pay(String reference, String lastName, String method, AppLang lang) => _call(
        () => _dio.post<dynamic>('/api/v1/bookings/$reference/payments', data: {'method': method, 'lastName': lastName, 'lang': lang.name}),
        (d) => PaymentInitiation.fromJson(d as Json),
      );

  /// Used by the in-app test gateway: follows the provider return URL and reads the booking back.
  Future<BookingView> followPaymentReturn(String url) {
    final uri = Uri.parse(url);
    return _call(
      () => _dio.get<dynamic>('/api/v1/payments/return', queryParameters: uri.queryParameters, options: Options(headers: {'Accept': 'application/json'})),
      (d) => BookingView.fromJson(d as Json),
    );
  }

  Future<CancellationResult> cancel(String reference, String lastName, {required bool dryRun, required AppLang lang}) => _call(
        () => _dio.post<dynamic>('/api/v1/bookings/$reference/cancel', data: {'lastName': lastName, 'dryRun': dryRun, 'lang': lang.name}),
        (d) => CancellationResult.fromJson(d as Json),
      );

  Future<List<VesselPosition>> vessels() => _call(
        () => _dio.get<dynamic>('/api/v1/live/vessels'),
        (d) => (d as List<dynamic>).map((e) => VesselPosition.fromJson(e as Json)).toList(),
      );

  Future<List<Guide>> guides() => _call(
        () => _dio.get<dynamic>('/api/v1/content/guides'),
        (d) => (d as List<dynamic>).map((e) => Guide.fromJson(e as Json)).toList(),
      );

  Future<List<Deal>> deals() => _call(
        () => _dio.get<dynamic>('/api/v1/content/deals'),
        (d) => (d as List<dynamic>).map((e) => Deal.fromJson(e as Json)).toList(),
      );

  /// Live ship positions over WebSocket, reconnecting with exponential backoff and jitter.
  Stream<List<VesselPosition>> liveStream() {
    late StreamController<List<VesselPosition>> controller;
    WebSocketChannel? channel;
    var attempt = 0;
    var closed = false;

    Future<void> connect() async {
      while (!closed) {
        try {
          channel = WebSocketChannel.connect(Uri.parse('${AppConfig.wsBaseUrl}/ws/live'));
          await channel!.ready;
          attempt = 0;
          await for (final message in channel!.stream) {
            final list = (jsonDecode(message as String) as List<dynamic>).map((e) => VesselPosition.fromJson(e as Json)).toList();
            if (!controller.isClosed) controller.add(list);
          }
        } catch (_) {
          // fall through to the backoff below
        }
        if (closed) break;
        attempt++;
        final delay = min(30, pow(2, attempt).toInt()) + Random().nextDouble();
        await Future<void>.delayed(Duration(milliseconds: (delay * 1000).round()));
      }
    }

    controller = StreamController<List<VesselPosition>>(
      onListen: connect,
      onCancel: () async {
        closed = true;
        await channel?.sink.close();
        await controller.close();
      },
    );
    return controller.stream;
  }
}

/// Retries idempotent GETs on network glitches (mobile networks in ports are flaky).
class _RetryInterceptor extends Interceptor {
  _RetryInterceptor(this._dio);
  final Dio _dio;

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    final options = err.requestOptions;
    final retries = (options.extra['retries'] as int?) ?? 0;
    final transient = err.type == DioExceptionType.connectionError ||
        err.type == DioExceptionType.connectionTimeout ||
        err.type == DioExceptionType.receiveTimeout ||
        (err.response?.statusCode ?? 0) >= 502;
    if (options.method == 'GET' && transient && retries < 2) {
      options.extra['retries'] = retries + 1;
      await Future<void>.delayed(Duration(milliseconds: 400 * (retries + 1)));
      try {
        return handler.resolve(await _dio.fetch<dynamic>(options));
      } on DioException catch (e) {
        return handler.next(e);
      }
    }
    handler.next(err);
  }
}
