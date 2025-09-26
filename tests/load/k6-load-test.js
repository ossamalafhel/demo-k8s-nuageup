import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// Custom metrics
const errorRate = new Rate('errors');
const transactionSuccess = new Rate('transaction_success');
const apiLatency = new Trend('api_latency');

// Test configuration
export const options = {
  // Test stages
  stages: [
    { duration: '1m', target: 10 },   // Warm-up
    { duration: '3m', target: 50 },   // Ramp-up
    { duration: '5m', target: 100 },  // Peak load
    { duration: '3m', target: 50 },   // Scale down
    { duration: '1m', target: 0 },    // Cool down
  ],
  
  // Thresholds for pass/fail criteria
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'], // 95% requests under 500ms
    errors: ['rate<0.01'],                          // Error rate under 1%
    transaction_success: ['rate>0.95'],             // 95% transaction success
    http_req_failed: ['rate<0.05'],                 // HTTP failure rate under 5%
  },
  
  // Tags and metadata
  tags: {
    test_type: 'load',
    environment: __ENV.ENVIRONMENT || 'test',
  },
  
  // Options for cloud execution (if using k6 cloud)
  ext: {
    loadimpact: {
      projectID: 3478725,
      name: 'Banking Demo Load Test',
    },
  },
};

// Test configuration from environment
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'test-api-key';

// Test data
const testAccounts = [
  'ACC0000000001',
  'ACC0000000002',
  'ACC0000000003',
  'ACC0000000004',
  'ACC0000000005',
];

const transactionTypes = ['DEPOSIT', 'WITHDRAWAL', 'TRANSFER'];
const currencies = ['USD', 'EUR', 'GBP'];

// Helper functions
function generateTransaction() {
  return {
    accountId: testAccounts[randomIntBetween(0, testAccounts.length - 1)],
    transactionType: transactionTypes[randomIntBetween(0, transactionTypes.length - 1)],
    amount: (Math.random() * 1000 + 10).toFixed(2),
    currency: currencies[randomIntBetween(0, currencies.length - 1)],
    description: `Load test transaction ${Date.now()}`,
  };
}

function checkResponse(res, expectedStatus = 200) {
  const success = check(res, {
    [`status is ${expectedStatus}`]: (r) => r.status === expectedStatus,
    'response time < 500ms': (r) => r.timings.duration < 500,
    'response time < 1000ms': (r) => r.timings.duration < 1000,
    'has valid JSON': (r) => {
      try {
        JSON.parse(r.body);
        return true;
      } catch (e) {
        return false;
      }
    },
  });
  
  errorRate.add(!success);
  apiLatency.add(res.timings.duration);
  
  return success;
}

// Setup function (runs once)
export function setup() {
  console.log('🚀 Starting load test...');
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`VUs: ${options.stages.map(s => s.target).join(' → ')}`);
  
  // Verify service is up
  const healthCheck = http.get(`${BASE_URL}/actuator/health`);
  if (healthCheck.status !== 200) {
    throw new Error('Service is not healthy!');
  }
  
  return {
    startTime: new Date().toISOString(),
  };
}

// Main test scenario
export default function (data) {
  // Headers for all requests
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': API_KEY,
      'X-Request-ID': `k6-${__VU}-${__ITER}`,
    },
    tags: {
      scenario: 'main',
    },
    timeout: '30s',
  };

  // Scenario 1: Health checks (10% of traffic)
  group('Health Checks', function () {
    if (Math.random() < 0.1) {
      const responses = http.batch([
        ['GET', `${BASE_URL}/actuator/health`, null, params],
        ['GET', `${BASE_URL}/api/v1/health`, null, params],
      ]);
      
      responses.forEach(res => checkResponse(res));
    }
  });

  // Scenario 2: Read transactions (60% of traffic)
  group('Read Operations', function () {
    if (Math.random() < 0.6) {
      // List transactions
      const listRes = http.get(
        `${BASE_URL}/api/v1/transactions?page=0&size=20`,
        params
      );
      checkResponse(listRes);
      
      // Get specific transaction (if list was successful)
      if (listRes.status === 200) {
        try {
          const transactions = JSON.parse(listRes.body).content;
          if (transactions && transactions.length > 0) {
            const transactionId = transactions[0].id;
            const getRes = http.get(
              `${BASE_URL}/api/v1/transactions/${transactionId}`,
              params
            );
            checkResponse(getRes);
          }
        } catch (e) {
          console.error('Failed to parse transaction list:', e);
        }
      }
      
      // Get transactions by account
      const accountId = testAccounts[randomIntBetween(0, testAccounts.length - 1)];
      const accountRes = http.get(
        `${BASE_URL}/api/v1/transactions/account/${accountId}`,
        params
      );
      checkResponse(accountRes);
    }
  });

  // Scenario 3: Create transactions (25% of traffic)
  group('Write Operations', function () {
    if (Math.random() < 0.25) {
      const transaction = generateTransaction();
      const createRes = http.post(
        `${BASE_URL}/api/v1/transactions`,
        JSON.stringify(transaction),
        params
      );
      
      const success = checkResponse(createRes, 201);
      transactionSuccess.add(success);
      
      // If creation successful, try to update it
      if (success && createRes.status === 201) {
        try {
          const createdTransaction = JSON.parse(createRes.body);
          const transactionId = createdTransaction.id;
          
          sleep(0.5); // Small delay before update
          
          const updateData = {
            ...createdTransaction,
            description: `Updated: ${createdTransaction.description}`,
          };
          
          const updateRes = http.put(
            `${BASE_URL}/api/v1/transactions/${transactionId}`,
            JSON.stringify(updateData),
            params
          );
          checkResponse(updateRes);
        } catch (e) {
          console.error('Failed to update transaction:', e);
        }
      }
    }
  });

  // Scenario 4: Statistics (5% of traffic)
  group('Statistics', function () {
    if (Math.random() < 0.05) {
      const statsRes = http.get(
        `${BASE_URL}/api/v1/transactions/statistics`,
        params
      );
      checkResponse(statsRes);
    }
  });

  // Scenario 5: Stress test circuit breaker (1% of traffic)
  group('Circuit Breaker Test', function () {
    if (Math.random() < 0.01) {
      // Intentionally cause errors to test circuit breaker
      const badTransaction = {
        accountId: 'INVALID',
        transactionType: 'INVALID',
        amount: -100, // Invalid amount
        currency: 'XXX',
      };
      
      const errorRes = http.post(
        `${BASE_URL}/api/v1/transactions`,
        JSON.stringify(badTransaction),
        params
      );
      
      // Expect 400 Bad Request
      check(errorRes, {
        'error returns 400': (r) => r.status === 400,
      });
    }
  });

  // Think time between operations
  sleep(randomIntBetween(1, 3));
}

// Teardown function (runs once after test)
export function teardown(data) {
  console.log('🏁 Load test completed!');
  console.log(`Start time: ${data.startTime}`);
  console.log(`End time: ${new Date().toISOString()}`);
  
  // Final health check
  const finalHealth = http.get(`${BASE_URL}/actuator/health`);
  if (finalHealth.status !== 200) {
    console.error('⚠️  Service unhealthy after test!');
  } else {
    console.log('✅ Service still healthy after test');
  }
}

// Custom summary (optional)
export function handleSummary(data) {
  const summary = {
    'Test Summary': {
      'Total Requests': data.metrics.http_reqs.values.count,
      'Failed Requests': data.metrics.http_req_failed.values.passes,
      'Average Response Time': Math.round(data.metrics.http_req_duration.values.avg),
      'P95 Response Time': Math.round(data.metrics.http_req_duration.values['p(95)']),
      'P99 Response Time': Math.round(data.metrics.http_req_duration.values['p(99)']),
      'Error Rate': `${(data.metrics.errors.values.rate * 100).toFixed(2)}%`,
      'Transaction Success Rate': `${(data.metrics.transaction_success.values.rate * 100).toFixed(2)}%`,
    },
  };
  
  // Console output
  console.log('\n📊 Test Results:');
  Object.entries(summary['Test Summary']).forEach(([key, value]) => {
    console.log(`  ${key}: ${value}`);
  });
  
  // Return multiple outputs
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }), // Terminal
    'summary.json': JSON.stringify(data, null, 2),                     // JSON file
    'summary.html': htmlReport(data),                                  // HTML report
  };
}

// HTML report generator (simplified)
function htmlReport(data) {
  return `
<!DOCTYPE html>
<html>
<head>
    <title>Load Test Results</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; }
        .metric { margin: 10px 0; padding: 10px; background: #f0f0f0; }
        .pass { color: green; }
        .fail { color: red; }
    </style>
</head>
<body>
    <h1>Banking Demo Load Test Results</h1>
    <div class="metric">
        <strong>Total Requests:</strong> ${data.metrics.http_reqs.values.count}
    </div>
    <div class="metric">
        <strong>Average Response Time:</strong> ${Math.round(data.metrics.http_req_duration.values.avg)}ms
    </div>
    <div class="metric">
        <strong>Error Rate:</strong> 
        <span class="${data.metrics.errors.values.rate < 0.01 ? 'pass' : 'fail'}">
            ${(data.metrics.errors.values.rate * 100).toFixed(2)}%
        </span>
    </div>
</body>
</html>
  `;
}