{
  "read": "2018-06-25T17:59:49.76013927Z",
  "preread": "0001-01-01T00:00:00Z",
  "pids_stats": {
    "current": 3
  },
  "blkio_stats": {
    "io_service_bytes_recursive": [
      {
        "major": 8,
        "minor": 0,
        "op": "Read",
        "value": 205672448
      },
      {
        "major": 8,
        "minor": 0,
        "op": "Write",
        "value": 97959936
      },
      {
        "major": 8,
        "minor": 0,
        "op": "Sync",
        "value": 97959936
      },
      {
        "major": 8,
        "minor": 0,
        "op": "Async",
        "value": 205672448
      },
      {
        "major": 8,
        "minor": 0,
        "op": "Total",
        "value": 303632384
      }
    ],
    "io_serviced_recursive": [
      {
        "major": 8,
        "minor": 0,
        "op": "Read",
        "value": 7475
      },
      {
        "major": 8,
        "minor": 0,
        "op": "Write",
        "value": 3183
      },
      {
        "major": 8,
        "minor": 0,
        "op": "Sync",
        "value": 3183
      },
      {
        "major": 8,
        "minor": 0,
        "op": "Async",
        "value": 7475
      },
      {
        "major": 8,
        "minor": 0,
        "op": "Total",
        "value": 10658
      }
    ],
    "io_queue_recursive": [],
    "io_service_time_recursive": [],
    "io_wait_time_recursive": [],
    "io_merged_recursive": [],
    "io_time_recursive": [],
    "sectors_recursive": []
  },
  "num_procs": 0,
  "storage_stats": {},
  "cpu_stats": {
    "cpu_usage": {
      "total_usage": 171249012213,
      "percpu_usage": [
        5793687384,
        94217589311,
        65407422713,
        5830312805
      ],
      "usage_in_kernelmode": 5980000000,
      "usage_in_usermode": 165280000000
    },
    "system_cpu_usage": 4437230860000000,
    "online_cpus": 4,
    "throttling_data": {
      "periods": 0,
      "throttled_periods": 0,
      "throttled_time": 0
    }
  },
  "precpu_stats": {
    "cpu_usage": {
      "total_usage": 0,
      "usage_in_kernelmode": 0,
      "usage_in_usermode": 0
    },
    "throttling_data": {
      "periods": 0,
      "throttled_periods": 0,
      "throttled_time": 0
    }
  },
  "memory_stats": {
    "usage": 134094848,
    "max_usage": 366649344,
    "stats": {
      "active_anon": 1449984,
      "active_file": 61980672,
      "cache": 132435968,
      "dirty": 0,
      "hierarchical_memory_limit": 9223372036854771712,
      "hierarchical_memsw_limit": 9223372036854771712,
      "inactive_anon": 208896,
      "inactive_file": 70455296,
      "mapped_file": 1773568,
      "pgfault": 318997,
      "pgmajfault": 1224,
      "pgpgin": 281234,
      "pgpgout": 248496,
      "rss": 1658880,
      "rss_huge": 0,
      "total_active_anon": 1449984,
      "total_active_file": 61980672,
      "total_cache": 132435968,
      "total_dirty": 0,
      "total_inactive_anon": 208896,
      "total_inactive_file": 70455296,
      "total_mapped_file": 1773568,
      "total_pgfault": 318997,
      "total_pgmajfault": 1224,
      "total_pgpgin": 281234,
      "total_pgpgout": 248496,
      "total_rss": 1658880,
      "total_rss_huge": 0,
      "total_unevictable": 0,
      "total_writeback": 0,
      "unevictable": 0,
      "writeback": 0
    },
    "limit": 8080965632
  },
  "name": "/gallant_spence",
  "id": "1b6c5ae96bcd1e8ca982ce8422b361e34a7d139257ea5214548d0fa22738aa92"
}



func calculateCPUPercentUnix(previousCPU, previousSystem uint64, v *types.StatsJSON) float64 
{
  var 
  (
    cpuPercent = 0.0
    // calculate the change for the cpu usage of the container in between readings
    cpuDelta = float64(stats['cpu_stats']['cpu_usage']['total_usage'] - float64(previousCPU)
    // calculate the change for the entire system between readings
    systemDelta = float64(stats['cpu_stats']['system_cpu_usage']) - float64(previousSystem)
  )

  if systemDelta > 0.0 && cpuDelta > 0.0 
  {
    cpuPercent = (cpuDelta / systemDelta) * float64(len(  stats['cpu_stats']['cpu_usage']['percpu_usage'] )) * 100.0
  }

  return cpuPercent
}
