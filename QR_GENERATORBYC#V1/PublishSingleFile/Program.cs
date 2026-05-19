using System.Windows.Forms;
using Jones.Activation;

namespace QRCodeTool;

static class Program
{
    [STAThread]
    static void Main()
    {
        ApplicationConfiguration.Initialize();

        if (!ActivationGuard.LaunchWithProtection("QRTOOL"))
        {
            return;
        }

        Application.Run(new Form1());
        ActivationGuard.StopPeriodicCheck();
    }
}
