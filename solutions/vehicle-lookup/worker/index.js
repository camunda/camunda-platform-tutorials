const http = require('http');

const BASE_URL = 'http://localhost:8080';
const AUTH = 'Basic ' + Buffer.from('demo:demo').toString('base64');
const JOB_TYPE = 'io.camunda.demo:calculate-eligibility';

async function request(method, path, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(BASE_URL + path);
    const opts = {
      hostname: url.hostname,
      port: url.port || 80,
      path: url.pathname,
      method,
      headers: {
        'Authorization': AUTH,
        'Content-Type': 'application/json',
      },
    };
    const req = http.request(opts, (res) => {
      let data = '';
      res.on('data', (chunk) => data += chunk);
      res.on('end', () => {
        try { resolve(JSON.parse(data)); } catch { resolve(data); }
      });
    });
    req.on('error', reject);
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

async function poll() {
  const activation = await request('POST', '/v2/jobs/activation', {
    type: JOB_TYPE,
    maxJobsToActivate: 5,
    timeout: 30000,
    worker: 'calculate-eligibility-worker',
  });

  const jobs = activation.jobs ?? [];
  for (const job of jobs) {
    const { make, model, year, 'vehicle type': vehicleType } = job.variables ?? {};

    let riskScore = 50;
    if (['PASSENGER CAR', 'MULTIPURPOSE PASSENGER VEHICLE (MPV)'].includes(vehicleType)) {
      riskScore -= 20;
    }
    if (year >= 2018) {
      riskScore -= 15;
    }

    const isEligible = riskScore <= 40;

    console.log(`${make} ${model} ${year} | type=${vehicleType} | score=${riskScore} | isEligible=${isEligible}`);

    await request('POST', `/v2/jobs/${job.jobKey}/completion`, {
      variables: { isEligible, riskScore },
    });
  }
}

console.log(`Worker polling for ${JOB_TYPE}...`);
setInterval(poll, 1000);
poll();
