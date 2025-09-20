-- CASO A QUERY RETORNE VALORES ELEVADOS EM DETERMINADO INTERVALO, VERIFIQUE SE A VARIAÇÃO NO CAMPO "DATETIME" FOI MAIOR OU MENOR DO QUE AS VARIAÇÕES ANTERIORES
-- VARIAÇÕES PARA MAIS  = METRICAS TRIPLICADAS
-- VARIAÇÕES PARA MENOS = METRICAS DUPLICADAS
WITH now() - INTERVAL 10 MINUTE AS t_from, now() AS t_to
SELECT datetime,
       product,
       allowed,
       sum(delta) AS rps
FROM (SELECT ts AS datetime,
             product,
             allowed,
             delta
      FROM (SELECT d.timestamp       AS    ts,
                   lagInFrame(d.timestamp) OVER (PARTITION BY d.id ORDER BY d.timestamp) AS prev_ts, t.tags['product'] AS product,
                   t.tags['allowed'] AS    allowed,
                   greatest(
                           d.value - lagInFrame(d.value) OVER (PARTITION BY d.id ORDER BY d.timestamp), 0
                   )                 AS    delta
            FROM timeSeriesData('metrics', 'prom_rw') AS d
                     JOIN timeSeriesTags('metrics', 'prom_rw') AS t USING id
            WHERE t.metric_name = 'app_rate_limit_product_requests_total'
              AND d.timestamp <= t_to
              AND t.tags['allowed'] = 'true')
      WHERE ts BETWEEN t_from AND t_to
        AND prev_ts >= t_from -- delta totalmente dentro da janela
     )
GROUP BY datetime, product, allowed
ORDER BY datetime, product, allowed;
