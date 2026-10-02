import 'package:flutter/material.dart';

import '../core/l10n.dart';
import '../core/theme.dart';
import '../data/api_client.dart';

class LoadingView extends StatelessWidget {
  const LoadingView({super.key, this.label});
  final String? label;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            const CircularProgressIndicator(color: WaveColors.navy),
            const SizedBox(height: 14),
            Text(label ?? context.t('common.loading'), style: const TextStyle(color: WaveColors.muted)),
          ]),
        ),
      );
}

class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.error, this.onRetry});
  final Object error;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final api = error is ApiException ? error as ApiException : null;
    final message = api == null
        ? context.t('common.error')
        : api.isOffline
            ? context.t('common.offline')
            : api.message;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Icon(api?.isOffline == true ? Icons.wifi_off_rounded : Icons.error_outline_rounded, size: 44, color: WaveColors.muted),
          const SizedBox(height: 12),
          Text(message, textAlign: TextAlign.center, style: const TextStyle(fontSize: 15)),
          if (api != null && api.violations.length > 1) ...[
            const SizedBox(height: 8),
            for (final v in api.violations.skip(1))
              Text('• ${v.message}', textAlign: TextAlign.center, style: const TextStyle(color: WaveColors.muted, fontSize: 13)),
          ],
          if (onRetry != null) ...[
            const SizedBox(height: 16),
            OutlinedButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh), label: Text(context.t('common.retry'))),
          ],
        ]),
      ),
    );
  }
}

/// Coloured box for rule notices (documents, regulations, minors...).
class NoticeBox extends StatelessWidget {
  const NoticeBox({super.key, required this.message, this.severity = 'INFO', this.title});
  final String message;
  final String severity;
  final String? title;

  @override
  Widget build(BuildContext context) {
    final (Color bg, Color fg, IconData icon) = switch (severity) {
      'ERROR' => (const Color(0xFFFDECEA), WaveColors.danger, Icons.block_rounded),
      'WARNING' => (const Color(0xFFFFF4E0), WaveColors.warning, Icons.warning_amber_rounded),
      _ => (const Color(0xFFEAF2FC), WaveColors.blue, Icons.info_outline_rounded),
    };
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(12)),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(icon, color: fg, size: 20),
        const SizedBox(width: 10),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            if (title != null) Text(title!, style: TextStyle(fontWeight: FontWeight.w600, color: fg)),
            Text(message, style: const TextStyle(fontSize: 13.5, height: 1.4, color: WaveColors.navyInk)),
          ]),
        ),
      ]),
    );
  }
}
