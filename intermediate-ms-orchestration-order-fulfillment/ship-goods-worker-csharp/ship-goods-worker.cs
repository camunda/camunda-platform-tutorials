using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Zeebe.Client;
using Zeebe.Client.Api.Responses;
using Zeebe.Client.Api.Worker;
using Zeebe.Client.Impl.Builder;
using Newtonsoft.Json.Linq;
using System.Collections.Generic;
using Newtonsoft.Json;

namespace DotNet_Camunda8_getting_started 
{
    class ZeebeClient
    {
        private static readonly String _ClientID = "xxx";
        private static readonly String _ClientSecret = "xxx";
        private static readonly String _ContactPoint = "xxx"; // = ZEEBE_ADDRESS 
        private static readonly String _JobType = "shipGoods"; 


        public static IZeebeClient zeebeClient;

        static async Task Main(string[] args)
        {
            zeebeClient = CamundaCloudClientBuilder
                                .Builder()
                                .UseClientId(_ClientID)
                                .UseClientSecret(_ClientSecret)
                                .UseContactPoint(_ContactPoint)
                                .Build();


            // Starting the Job Worker
            using (var signal = new EventWaitHandle(false, EventResetMode.AutoReset))
            {
                zeebeClient.NewWorker()
                         .JobType(_JobType)
                         .Handler(ShipGoods)
                         .MaxJobsActive(5)
                         .Name(Environment.MachineName)
                         .AutoCompletion()
                         .PollInterval(TimeSpan.FromSeconds(1))
                         .Timeout(TimeSpan.FromSeconds(10))
                         .Open();

                signal.WaitOne();
            }
        }

        
        private static void ShipGoods(IJobClient jobClient, IJob job)
        {
            JObject jsonObject = JObject.Parse(job.Variables);
            bool goodsShipped = true;

            Console.WriteLine("Shipping goods");
            
            Dictionary<String, bool> shipGoodsVariables = new Dictionary<String, bool>();
            shipGoodsVariables.Add("goodsShipped", goodsShipped);

            jobClient.NewCompleteJobCommand(job.Key)
                    .Variables(JsonConvert.SerializeObject(shipGoodsVariables))
                    .Send()
                    .GetAwaiter()
                    .GetResult();
            
            Console.WriteLine("Goods shipped");
        }  
    }
}